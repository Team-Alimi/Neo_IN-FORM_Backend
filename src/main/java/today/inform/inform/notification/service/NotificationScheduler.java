package today.inform.inform.notification.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.notification.repository.NotificationRepository;

/**
 * SYS-03 마감 D-1 알림 생성 · SYS-04 오래된 알림 정리.
 *
 * <p><b>알림을 만드는 유일한 경로입니다.</b> 이 클래스가 없던 동안 알림 조회 API 4개는
 * 전부 동작했지만 목록은 항상 비어 있었고 배지는 0 이었습니다 — 오류가 없어서
 * "기능이 아직 안 됐다" 가 아니라 "알림이 올 일이 없었다" 로 보였습니다.
 *
 * <h2>★ 기준일을 앱에서 계산합니다</h2>
 * {@code LocalDate.now()} 는 앱 시계(KST, {@code -Duser.timezone=Asia/Seoul})입니다.
 * SQL 의 {@code CURRENT_DATE} 를 쓰면 DB 컨테이너(UTC)와 KST 00~09시에 하루 어긋나고,
 * <b>이 배치는 바로 그 구간의 끝인 09시에 돕니다.</b> UTC 로는 아직 전날이라
 * 하루 치가 통째로 밀리거나 겹칩니다.
 *
 * <h2>★ 배치가 실패해도 서비스는 멈추지 않습니다</h2>
 * 예외를 밖으로 던지면 스케줄러 스레드가 죽어 <b>다음 주기도 돌지 않습니다.</b>
 * 그래서 잡아서 로그만 남깁니다. 알림은 부가 기능이고, 다음 날 다시 돕니다 —
 * {@code ON CONFLICT} 덕에 재실행이 안전해서 이 선택이 가능합니다.
 *
 * <h2>★ ShedLock 이 필요하지 않습니다</h2>
 * 인스턴스를 여러 대로 늘려도 중복이 생기지 않습니다. 생성은 유니크 키가 막고,
 * 정리는 같은 조건의 DELETE 라 두 번 돌아도 결과가 같습니다.
 * ({@code ArticleViewCounter#flush} 는 <b>증분</b>이라 사정이 다릅니다 — 그쪽은 필요합니다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    /** 명세 SYS-04. 이보다 오래된 알림은 지웁니다. */
    private static final Duration RETENTION = Duration.ofDays(30);

    private final NotificationRepository notificationRepository;

    /**
     * SYS-03. 매일 09:00 KST, <b>내일 마감인</b> 공지를 북마크한 사용자에게 알림을 만듭니다.
     *
     * <p>{@code zone} 을 명시합니다 — JVM 기본 시간대에 기대면 로컬·CI 에서
     * 다른 시각에 돌고, 그 차이는 운영에서만 드러납니다.
     *
     * <p>비활성화하려면 설정값을 {@code -} 로 두세요(Spring 이 등록을 건너뜁니다).
     */
    @Scheduled(cron = "${inform.notification.deadline-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void createDeadlineNotifications() {
        try {
            runDeadlineNotifications();
        } catch (RuntimeException e) {
            log.error("마감 D-1 알림 생성 실패", e);
        }
    }

    /**
     * 생성 본체. <b>실패를 감추지 않고 그대로 던집니다.</b>
     *
     * <p>배치 경로({@link #createDeadlineNotifications()})는 예외를 삼키지만 이쪽은 아닙니다 —
     * 관리자가 손으로 돌렸을 때 조용히 0건으로 끝나면 "대상이 없는 것" 과 "실패한 것" 을
     * 구분할 수 없습니다. 수동 실행은 호출한 사람이 결과를 봐야 합니다.
     *
     * <p>기준일은 <b>언제나 "내일"</b> 입니다. 날짜를 인자로 받지 않는 것이 중요합니다 —
     * 임의 날짜를 허용하면 두 달 뒤 마감인 공지에 "마감이 하루 남았습니다" 를 보낼 수 있고,
     * 그건 사용자에게 거짓을 알리는 것입니다. 문구가 날짜를 전제로 고정돼 있습니다.
     *
     * @return 실제로 만들어진 건수. 중복으로 건너뛴 것은 세지 않습니다
     */
    @Transactional
    public DeadlineRun runDeadlineNotifications() {
        LocalDate deadline = LocalDate.now().plusDays(1);
        int created = notificationRepository.createDeadlineNotifications(deadline);
        // 0 건도 정상입니다 — 내일 마감인 공지를 아무도 북마크하지 않은 날입니다.
        log.info("마감 D-1 알림 생성 완료. 기준 마감일={}, 생성={}건", deadline, created);
        return new DeadlineRun(deadline, created);
    }

    /**
     * 생성 결과.
     *
     * @param deadline 이번 실행의 기준 마감일(= 내일)
     * @param created  새로 만들어진 건수. <b>0 이 정상일 수 있습니다</b> —
     *                 이미 만들어 둔 것은 중복으로 건너뛰고, 대상이 없어도 0 입니다
     */
    public record DeadlineRun(LocalDate deadline, int created) {
    }

    /**
     * SYS-04. 매일 04:00 KST, 생성 30일이 지난 알림을 지웁니다.
     *
     * <p>생성(09:00)과 시각을 떼어 둡니다. 같은 시각에 돌면 한쪽이 만든 것을
     * 다른 쪽이 들여다보는 구간이 생기고, 로그에서 두 작업이 섞여 읽기 어려워집니다.
     */
    @Scheduled(cron = "${inform.notification.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void deleteOldNotifications() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(RETENTION);
        try {
            int deleted = notificationRepository.deleteCreatedBefore(cutoff);
            log.info("오래된 알림 정리 완료. 기준={}, 삭제={}건", cutoff, deleted);
        } catch (RuntimeException e) {
            log.error("오래된 알림 정리 실패. 기준={}", cutoff, e);
        }
    }
}
