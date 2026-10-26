package today.inform.inform.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import today.inform.inform.mail.MailSender;

/**
 * SES 대역. <b>검증하고 싶은 것은 "누구에게 보내는가" 입니다.</b>
 *
 * <p>수신 토글이 꺼진 사용자를 거르는지, 탈퇴자를 빼는지, 같은 알림으로 두 번 보내지 않는지 —
 * 전부 SES 없이 확인할 수 있습니다. 실제 SES 를 태우면 자격증명 없이는 빌드가 돌지 않고,
 * 돌더라도 <b>진짜 메일이 사용자에게 날아갑니다.</b>
 *
 * <p><b>★ 이 대역은 SES 연동 자체를 검증하지 못합니다.</b> 주소 형식·charset·
 * 자격증명·리전 설정은 {@code SesMailSender} 안에 있고 여기엔 없습니다.
 * 그쪽은 검증된 수신 주소로 실제 한 통 보내 확인해야 합니다
 * ({@code FakeFileStorage} 와 {@code S3FileStorageTest} 의 관계와 같습니다).
 */
public class FakeMailSender implements MailSender {

    private final List<Sent> sent = new ArrayList<>();
    private boolean enabled = true;
    private RuntimeException failure;

    /** 보낸 한 통. */
    public record Sent(String to, String subject, String body) {
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (failure != null) {
            // ★ 기록하지 않습니다. 실패한 발송은 "보낸 것" 이 아니고,
            //   호출부가 email_sent_at 을 찍지 않아야 다음 주기에 재시도됩니다.
            throw failure;
        }
        sent.add(new Sent(to, subject, body));
    }

    /** 보낸 메일 전부. 순서는 발송 순서입니다. */
    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** 특정 주소로 간 건수. "안 보냈어야 할 사람" 검사에 씁니다. */
    public long countTo(String to) {
        return sent.stream().filter(mail -> mail.to().equals(to)).count();
    }

    /** {@code MAIL_FROM} 미설정 상황을 흉내 냅니다. */
    public void disable() {
        this.enabled = false;
    }

    /** 다음 발송부터 실패시킵니다. 재시도·{@code email_sent_at} 미기록을 검사할 때 씁니다. */
    public void failWith(RuntimeException error) {
        this.failure = error;
    }

    public void reset() {
        sent.clear();
        enabled = true;
        failure = null;
    }

    /**
     * 실제 {@code SesMailSender} 대신 이 대역을 주입합니다.
     *
     * <p>{@code @Primary} 라 같은 타입의 빈이 둘이어도 이쪽이 선택됩니다.
     */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public FakeMailSender fakeMailSender() {
            return new FakeMailSender();
        }
    }
}
