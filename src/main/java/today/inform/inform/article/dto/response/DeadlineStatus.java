package today.inform.inform.article.dto.response;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 마감 기준 파생값 (명세 2.8). <b>DB 컬럼이 아닙니다</b> — 조회 시각에 계산합니다.
 *
 * <p>컬럼으로 두면 매일 자정에 전 행을 갱신하는 배치가 필요하고, 그 배치가 밀리면
 * 화면이 "어제 기준" 을 보여 줍니다. 파생값이라 그런 어긋남이 아예 생기지 않습니다.
 *
 * <p><b>v1 문제</b>: {@code status} 필드가 마감 상태를 뜻했는데 관리자 상태와 이름이 헷갈렸습니다.
 * v2 는 이것을 {@code deadline_status} 로 분리하고, 관리자 상태는 관리자 응답에만 둡니다.
 */
public enum DeadlineStatus {

    /** 아직 시작 전. */
    UPCOMING,

    /** 진행 중. */
    OPEN,

    /** 마감까지 사흘 이내. 화면이 강조합니다. */
    CLOSING_SOON,

    /** 마감됨. */
    CLOSED,

    /** 기간 정보가 없음 — 상시 안내. */
    ALWAYS;

    /** 마감 임박 판정 기준. 명세 2.8 의 "3일 이내". */
    public static final long CLOSING_SOON_DAYS = 3;

    /**
     * @param today 판정 기준일. 파라미터로 받는 이유는 테스트가 날짜를 고정할 수 있어야 하기 때문입니다 —
     *              {@code LocalDate.now()} 를 안에서 부르면 "오늘이 언제냐" 에 따라 결과가 흔들려
     *              경계 조건을 검증할 수 없습니다
     */
    public static DeadlineStatus of(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
        if (startsOn == null && endsOn == null) {
            return ALWAYS;
        }
        if (startsOn != null && today.isBefore(startsOn)) {
            return UPCOMING;
        }
        if (endsOn == null) {
            // 시작만 있고 끝이 없는 공지. 시작했으면 계속 열려 있습니다.
            return OPEN;
        }
        if (today.isAfter(endsOn)) {
            return CLOSED;
        }
        return ChronoUnit.DAYS.between(today, endsOn) <= CLOSING_SOON_DAYS ? CLOSING_SOON : OPEN;
    }

    /**
     * 위 {@link #of} 와 <b>같은 판정</b>을 SQL 로 옮긴 것. 목록의 마감 상태 필터가 씁니다.
     *
     * <p><b>★ 한쪽만 고치면 목록 필터와 카드 배지가 어긋납니다.</b> 분기 순서까지 1:1 로
     * 맞춰 두었으니 반드시 같이 고치세요. 그래서 이 상수를 리포지토리가 아니라
     * 판정 메서드 바로 아래에 둡니다 — 떨어뜨려 놓으면 언젠가 한쪽만 바뀝니다.
     *
     * <p><b>{@code UPCOMING} 을 마감 계산보다 먼저 보는 순서가 특히 중요합니다.</b>
     * "내일 시작해서 모레 마감" 인 공지는 마감이 3일 이내여도 {@code UPCOMING} 입니다.
     * 순서를 바꾸면 카드에는 "예정" 배지가 붙는데 마감임박 필터에 걸립니다.
     *
     * <p><b>{@code CURRENT_DATE} 를 쓰지 않습니다.</b> DB 컨테이너 시간대는 UTC 인데
     * 앱은 {@code -Duser.timezone=Asia/Seoul} 이라, KST 00~09시에 "오늘" 이 하루 어긋납니다.
     * 새벽에만 필터와 배지가 달라지는 버그는 재현이 거의 안 됩니다.
     * 앱이 계산한 날짜를 {@code :today} 로 넘겨 같은 기준을 쓰게 합니다.
     *
     * <p>{@code CAST} 를 붙이는 이유는 Hibernate 네이티브 쿼리에서 바인딩 파라미터의
     * 타입이 추론되지 않아 {@code date - date} 연산자를 못 찾는 경우가 있기 때문입니다.
     *
     * @param alias 공지 테이블 별칭 (목록 쿼리는 {@code a})
     */
    public static String sqlExpression(String alias) {
        return """
                CASE
                    WHEN %1$s.starts_on IS NULL AND %1$s.ends_on IS NULL THEN 'ALWAYS'
                    WHEN %1$s.starts_on IS NOT NULL
                         AND CAST(:today AS date) < %1$s.starts_on              THEN 'UPCOMING'
                    WHEN %1$s.ends_on IS NULL                                   THEN 'OPEN'
                    WHEN CAST(:today AS date) > %1$s.ends_on                    THEN 'CLOSED'
                    WHEN %1$s.ends_on - CAST(:today AS date) <= :closingSoonDays THEN 'CLOSING_SOON'
                    ELSE 'OPEN'
                END""".formatted(alias);
    }
}
