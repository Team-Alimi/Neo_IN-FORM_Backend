package today.inform.inform.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * NTF-05 메일 발송 한도.
 *
 * <p>값을 설정으로 빼는 이유는 <b>SES 한도가 심사에 따라 바뀌기 때문</b>입니다.
 * 샌드박스는 1통/초 · 200통/일이고, 프로덕션 액세스가 승인되면 그 둘이 함께 올라갑니다.
 * 코드에 박아 두면 승인될 때마다 배포해야 합니다.
 *
 * @param maxAge       이보다 오래 전에 만들어진 알림은 <b>메일을 보내지 않습니다.</b>
 *                     메일 수신을 꺼 둔 사용자의 알림은 {@code email_sent_at} 이 영원히
 *                     NULL 로 남는데, 창이 없으면 그 사용자가 나중에 토글을 켜는 순간
 *                     지난 30일치(SYS-04 보관 기간)가 한꺼번에 날아갑니다.
 *                     V18 백필로 막은 것과 같은 종류의 소급 발송입니다
 * @param batchSize    한 번에 보낼 최대 통수. 샌드박스의 일일 한도(200)를 한 번에
 *                     태우지 않도록 잘라 둡니다. 남은 것은 다음 주기에 이어 보냅니다
 * @param sendInterval 발송 사이 최소 간격. SES 의 {@code MaxSendRate} 가 현재 1통/초라
 *                     연달아 쏘면 {@code TooManyRequestsException} 이 납니다.
 *                     실패한 알림은 {@code email_sent_at} 이 NULL 로 남아 재시도되지만,
 *                     한도를 때리는 호출을 반복할 이유가 없습니다
 * @param lockTtl      배치 락 유지 시간. <b>한 번의 실행에 걸리는 최대 시간보다 길어야
 *                     합니다</b> — 실행 중에 만료되면 다른 인스턴스가 같은 행을 집어
 *                     같은 메일을 또 보냅니다. 생성자에서 그 관계를 검사합니다
 * @param baseUrl      수신거부 링크의 앞부분. <b>API 호스트</b>입니다 — 수신거부 페이지를
 *                     이 백엔드가 직접 그립니다. 메일에 들어가는 주소라
 *                     운영에서 실제로 열리는 호스트여야 합니다
 * @param serviceUrl   메일 본문에서 "확인하기" 로 연결할 <b>서비스(프론트) 주소</b>.
 *                     공지 상세로 바로 보내지 않고 루트로 보냅니다 —
 *                     프론트의 상세 경로를 추측해 넣으면 죽은 링크가 메일에 박히고,
 *                     그 사실은 받은 사람이 눌러 본 뒤에야 드러납니다.
 *                     딥링크는 프론트에서 경로를 확정한 뒤에 붙입니다
 */
@ConfigurationProperties(prefix = "inform.notification.email")
public record NotificationEmailProperties(
        @DefaultValue("24h") Duration maxAge,
        @DefaultValue("100") int batchSize,
        @DefaultValue("1100ms") Duration sendInterval,
        @DefaultValue("10m") Duration lockTtl,
        @DefaultValue("https://api.inha-inform.today") String baseUrl,
        @DefaultValue("https://inha-inform.today") String serviceUrl) {

    public NotificationEmailProperties {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()
                || maxAge.compareTo(Duration.ofDays(7)) > 0) {
            // 상한을 7일로 두는 이유 — 창을 넓히는 것은 곧 "며칠 전 알림까지 메일로
            // 보낸다" 는 뜻입니다. 30일로 적어 두면 SYS-04 보관 기간과 같아져
            // 창이 사실상 없는 것과 같아집니다.
            throw new IllegalArgumentException(
                    "inform.notification.email.max-age 는 0 초과 7일 이하여야 합니다");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "inform.notification.email.batch-size 는 1 이상 1000 이하여야 합니다");
        }
        if (sendInterval == null || sendInterval.isNegative()
                || sendInterval.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException(
                    "inform.notification.email.send-interval 은 0 이상 10초 이하여야 합니다");
        }
        if (lockTtl == null || lockTtl.isZero() || lockTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "inform.notification.email.lock-ttl 은 0 보다 커야 합니다");
        }
        // ★ 이 검사가 중복 발송을 막습니다. batchSize 통을 sendInterval 간격으로 보내면
        //   최소 batchSize × sendInterval 이 걸리고, 거기에 SES 왕복 시간이 더 붙습니다.
        //   락이 그 전에 풀리면 다음 주기(또는 다른 인스턴스)가 아직 email_sent_at 이
        //   안 찍힌 같은 행들을 집어 같은 메일을 또 보냅니다. 되돌릴 수 없습니다.
        Duration worstCase = sendInterval.multipliedBy(batchSize).multipliedBy(2);
        if (lockTtl.compareTo(worstCase) < 0) {
            throw new IllegalArgumentException(
                    "inform.notification.email.lock-ttl 이 한 번의 실행 시간보다 짧습니다. "
                            + "batch-size × send-interval × 2 = " + worstCase + " 이상이어야 합니다");
        }
        requireUrl(baseUrl, "base-url");
        requireUrl(serviceUrl, "service-url");
    }

    private static void requireUrl(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("inform.notification.email." + key + " 이 필요합니다");
        }
        if (value.endsWith("/")) {
            // 뒤에 슬래시가 붙으면 링크가 //api/... 가 되어 호스트에 따라 404 가 납니다.
            // 그 사실은 메일을 받은 사용자가 눌러 본 뒤에야 드러납니다.
            throw new IllegalArgumentException(
                    "inform.notification.email." + key + " 뒤에 슬래시를 붙이지 마세요");
        }
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            // 스킴이 없으면 메일 클라이언트가 링크로 인식하지 못해 평문으로 남습니다.
            throw new IllegalArgumentException(
                    "inform.notification.email." + key + " 은 http:// 또는 https:// 로 시작해야 합니다");
        }
    }
}
