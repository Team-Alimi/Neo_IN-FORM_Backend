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
    private static final long CLOSING_SOON_DAYS = 3;

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
}
