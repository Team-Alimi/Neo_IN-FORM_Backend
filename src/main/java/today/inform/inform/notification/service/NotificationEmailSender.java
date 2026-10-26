package today.inform.inform.notification.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import today.inform.inform.mail.MailSender;
import today.inform.inform.notification.config.NotificationEmailProperties;
import today.inform.inform.notification.repository.NotificationRepository;
import today.inform.inform.notification.repository.PendingEmail;

/**
 * NTF-05 알림 메일 발송.
 *
 * <h2>★ 트랜잭션이 발송 루프를 감싸지 않습니다</h2>
 * 조회는 한 번의 읽기로 끝내고, 발송은 트랜잭션 <b>밖</b>에서 돌리고,
 * 기록은 한 통마다 즉시 커밋합니다({@code markEmailSent} 가 스스로 트랜잭션을 엽니다).
 * 루프 전체를 한 트랜잭션으로 묶으면 두 가지가 깨집니다.
 * <ul>
 *   <li><b>끝에서 롤백되면 메일은 이미 나갔는데 기록이 사라집니다.</b>
 *       다음 주기가 같은 메일을 또 보냅니다 — 되돌릴 수 없는 종류의 중복입니다</li>
 *   <li>SES 호출 사이의 대기(1통/초 제한) 동안 DB 커넥션을 수십 초 잡습니다</li>
 * </ul>
 *
 * <h2>★ 알림 생성과도 분리돼 있습니다</h2>
 * {@link NotificationScheduler} 가 09시에 알림을 만들고, 이 배치가 뒤따라 보냅니다.
 * 한 트랜잭션에 넣으면 <b>SES 실패가 알림 생성을 롤백</b>합니다 —
 * 메일이 안 간 것 때문에 인앱 알림까지 사라지는 건 손해가 큽니다.
 * 인앱 알림이 먼저 확정되고, 메일은 그 뒤에 따라붙는 부가 전달 수단입니다.
 *
 * <h2>★ 여기엔 락이 필요합니다 — 알림 생성과 다릅니다</h2>
 * 생성은 유니크 키 위의 {@code ON CONFLICT DO NOTHING} 이라 두 번 돌아도 1건이고,
 * 그래서 ShedLock 이 필요 없었습니다. <b>메일에는 그런 장치가 없습니다.</b>
 * 두 인스턴스가 같은 행을 집으면 사용자에게 같은 메일이 두 통 갑니다.
 * {@code email_sent_at} 은 보낸 <i>뒤에</i> 찍히므로 그 창을 막지 못합니다.
 * 그래서 실행 전체를 Redis 락으로 감쌉니다({@code ArticleSummaryGuard} 와 같은 방식).
 *
 * <p>Redis 가 죽어 락을 못 얻으면 이번 주기를 건너뜁니다. 메일이 늦는 것과
 * 두 통 가는 것 중에서는 늦는 쪽이 낫습니다.
 */
@Slf4j
@Component
public class NotificationEmailSender {

    private static final String LOCK_KEY = "inform:notification-email:lock";

    /** 내가 건 락만 품. 만료 뒤 다른 실행이 새로 건 락을 지우지 않게 토큰을 확인합니다. */
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    /**
     * 연속 실패 허용 횟수.
     *
     * <p>주소 하나가 잘못됐다고 나머지 전원의 메일을 막을 이유는 없어서 건당 실패는
     * 넘어갑니다. 반대로 <b>연달아</b> 실패하는 것은 주소 문제가 아니라
     * 자격증명·리전·발신 한도처럼 전체에 걸린 문제입니다. 그 상태로 루프를 끝까지 돌리면
     * 한도를 때리는 호출만 수십 번 반복하고 로그가 같은 오류로 뒤덮입니다.
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final NotificationRepository notificationRepository;
    private final MailSender mailSender;
    private final UnsubscribeTokenSigner unsubscribeTokenSigner;
    private final NotificationEmailProperties properties;
    private final StringRedisTemplate redis;

    /** 비활성 경고를 주기마다 찍지 않기 위한 표시. 로그가 같은 줄로 덮이면 다른 걸 못 봅니다. */
    private boolean disabledWarned;

    public NotificationEmailSender(
            NotificationRepository notificationRepository,
            MailSender mailSender,
            UnsubscribeTokenSigner unsubscribeTokenSigner,
            NotificationEmailProperties properties,
            StringRedisTemplate redis) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.unsubscribeTokenSigner = unsubscribeTokenSigner;
        this.properties = properties;
        this.redis = redis;
    }

    /**
     * 주기 실행.
     *
     * <p>생성 배치(09:00)처럼 하루 한 번이 아니라 짧은 주기로 도는 이유 —
     * SES 가 일시적으로 실패하면 {@code email_sent_at} 이 NULL 로 남아 재시도되는데,
     * 하루 한 번이면 그 재시도가 <b>24시간 뒤</b>입니다. 마감 하루 전 알림이
     * 마감 당일에 도착하면 보낸 의미가 없습니다.
     *
     * <p>예외를 삼킵니다. 던지면 스케줄러 스레드가 죽어 다음 주기도 돌지 않습니다.
     *
     * <p><b>{@code initialDelay} 가 왜 있는가</b> — {@code fixedDelay} 는 컨텍스트가 뜨는
     * 즉시 한 번 돕니다. 운영에서는 배포 직후 앱이 아직 자리잡기 전에 외부 발송이
     * 시작되는 것을 한 박자 늦추고, <b>테스트에서는 이 값을 크게 줘서 배치가
     * 테스트와 겹치지 않게</b> 합니다. 겹치면 테스트가 집으려던 행을 배치가 먼저
     * 보내 버려 "중복 발송 없음" 같은 검증이 엉뚱하게 깨집니다.
     */
    @Scheduled(
            fixedDelayString = "${inform.notification.email.poll-interval-ms:300000}",
            initialDelayString = "${inform.notification.email.initial-delay-ms:60000}")
    public void sendPendingEmails() {
        try {
            sendPending();
        } catch (RuntimeException e) {
            log.error("알림 메일 발송 배치 실패", e);
        }
    }

    /**
     * 발송 본체. <b>실패를 감추지 않고 그대로 던집니다.</b>
     *
     * <p>주기 경로({@link #sendPendingEmails()})는 예외를 삼키지만 이쪽은 아닙니다 —
     * 관리자가 손으로 돌렸을 때 조용히 0건으로 끝나면 "보낼 게 없는 것" 과
     * "실패한 것" 을 구분할 수 없습니다.
     *
     * @return 이번 실행의 집계
     */
    public EmailRun sendPending() {
        if (!mailSender.isEnabled()) {
            if (!disabledWarned) {
                // ★ 한 번은 반드시 남깁니다. MAIL_FROM 을 서버 .env 에 넣지 않으면
                //   기능이 조용히 꺼진 채 배포가 초록불로 끝납니다 —
                //   UPSTAGE_API_KEY 가 비어 AI 요약이 안 돌던 것과 같은 상황입니다.
                log.warn("MAIL_FROM 이 없어 알림 메일을 보내지 않습니다. 서버 .env 를 확인하세요");
                disabledWarned = true;
            }
            return EmailRun.nothingToDo();
        }

        String owner = acquireLock();
        if (owner == null) {
            log.debug("알림 메일 발송 락 획득 실패. 이번 주기를 건너뜁니다");
            return EmailRun.nothingToDo();
        }
        try {
            return send(loadPending());
        } finally {
            releaseLock(owner);
        }
    }

    private List<PendingEmail> loadPending() {
        // ★ 기준 시각을 앱 시계로 계산합니다. SQL 의 now() 를 쓰면 DB 컨테이너(UTC)와
        //   앱(KST)이 어긋나 창의 경계가 9시간 밀립니다.
        OffsetDateTime createdAfter = OffsetDateTime.now().minus(properties.maxAge());
        return notificationRepository.findPendingEmails(
                createdAfter, PageRequest.ofSize(properties.batchSize()));
    }

    private EmailRun send(List<PendingEmail> pending) {
        if (pending.isEmpty()) {
            return EmailRun.nothingToDo();
        }

        int sent = 0;
        int failed = 0;
        int duplicated = 0;
        int consecutiveFailures = 0;

        for (int index = 0; index < pending.size(); index++) {
            if (index > 0 && !pause()) {
                // 인터럽트는 종료 신호입니다. 남은 것은 email_sent_at 이 NULL 이라
                // 다음 기동의 첫 주기가 이어 보냅니다.
                log.warn("발송 중 인터럽트. 중단합니다. 보냄={}건, 남음={}건",
                        sent, pending.size() - index);
                break;
            }

            PendingEmail target = pending.get(index);
            try {
                mailSender.send(target.email(), subject(target), body(target));
            } catch (RuntimeException e) {
                failed++;
                consecutiveFailures++;
                // ★ 주소를 로그에 남기지 않습니다. 알림 id 로 역추적할 수 있고,
                //   로그는 주소를 담아도 되는 저장소가 아닙니다.
                log.warn("알림 메일 발송 실패. 알림 id={} (재시도 대상으로 남깁니다)",
                        target.notificationId(), e);
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    log.error("연속 {}건 실패. 개별 주소 문제가 아니라 발송 경로 전체의 문제로 "
                                    + "보고 중단합니다. 남음={}건",
                            consecutiveFailures, pending.size() - index - 1);
                    break;
                }
                continue;
            }
            consecutiveFailures = 0;

            // 여기부터는 메일이 이미 나갔습니다. 되돌릴 수 없으므로 기록을 반드시 남깁니다.
            if (notificationRepository.markEmailSent(target.notificationId()) == 0) {
                duplicated++;
                // 0 은 그 사이 다른 실행이 같은 행을 찍었다는 뜻 — 같은 메일이 두 통 갔습니다.
                // 락이 제 역할을 못 하고 있다는 신호라 error 로 남깁니다.
                log.error("발송 기록이 이미 있었습니다. 알림 id={} — 같은 메일이 중복 발송됐을 수 "
                        + "있습니다. 배치 락을 확인하세요", target.notificationId());
            } else {
                sent++;
            }
        }

        log.info("알림 메일 발송 완료. 보냄={}건, 실패={}건, 중복={}건, 대상={}건",
                sent, failed, duplicated, pending.size());
        return new EmailRun(sent, failed, duplicated, pending.size());
    }

    /**
     * 발송 간 간격을 지킵니다.
     *
     * @return 계속 보내도 되면 {@code true}. 인터럽트가 걸렸으면 {@code false}
     */
    private boolean pause() {
        long millis = properties.sendInterval().toMillis();
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            // ★ 삼키지 않고 플래그를 되돌립니다. 지우면 상위(스케줄러 종료)가
            //   종료 신호를 못 봅니다.
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 받은 편지함에서 어디서 온 메일인지 보여야 합니다. 제목만으로는 분간이 안 됩니다. */
    private String subject(PendingEmail target) {
        return "[INFORM] " + target.title();
    }

    /**
     * 평문 본문.
     *
     * <p><b>HTML 이 아닙니다.</b> 본문에 공지 제목이 그대로 들어가는데 HTML 로 조립하면
     * 꺾쇠가 섞인 제목에서 깨지거나 주입이 됩니다. 마감 알림에 서식이 필요하지도 않습니다.
     */
    private String body(PendingEmail target) {
        StringBuilder body = new StringBuilder()
                .append(target.message())
                .append("\n\n")
                .append("북마크한 공지의 마감일이 하루 남아 보내는 알림입니다.\n")
                .append("자세한 내용은 INFORM 에서 확인하세요.\n")
                .append(properties.serviceUrl())
                .append("\n");

        if (unsubscribeTokenSigner.isEnabled()) {
            body.append("\n----------------------------------------\n")
                    .append("메일 알림을 받고 싶지 않으시면 아래 주소에서 끄실 수 있습니다.\n")
                    .append(unsubscribeUrl(target.userId()))
                    .append("\n");
        }
        // UNSUBSCRIBE_SECRET 이 없으면 수신거부 문단을 아예 넣지 않습니다 —
        // 누를 수 없는 링크를 적어 두는 것보다 없는 게 낫습니다.
        // 그 경우에도 설정 화면에서 끌 수 있어 수신거부 수단 자체는 남아 있습니다.
        return body.toString();
    }

    private String unsubscribeUrl(Long userId) {
        return properties.baseUrl() + NotificationEmailPaths.UNSUBSCRIBE
                + "?token=" + unsubscribeTokenSigner.sign(userId);
    }

    private String acquireLock() {
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(LOCK_KEY, owner, properties.lockTtl());
        return Boolean.TRUE.equals(acquired) ? owner : null;
    }

    private void releaseLock(String owner) {
        try {
            redis.execute(RELEASE, List.of(LOCK_KEY), owner);
        } catch (RuntimeException e) {
            // 풀지 못해도 TTL 로 풀립니다. 여기서 던지면 발송 성공을 실패로 보고하게 됩니다.
            log.warn("알림 메일 발송 락 해제 실패. TTL 로 풀립니다", e);
        }
    }

    /**
     * 이번 실행의 집계.
     *
     * @param sent       보내고 기록까지 끝낸 건수
     * @param failed     발송이 실패해 <b>다음 주기에 재시도될</b> 건수
     * @param duplicated 보냈는데 기록이 이미 있던 건수.
     *                   <b>0 이 아니면 중복 발송이 일어났다는 뜻</b>입니다
     * @param targeted   이번 실행이 집어 온 건수
     */
    public record EmailRun(int sent, int failed, int duplicated, int targeted) {

        /** 보낼 것이 없었거나 발송이 꺼져 있었거나 락을 못 얻은 경우. */
        static EmailRun nothingToDo() {
            return new EmailRun(0, 0, 0, 0);
        }
    }
}
