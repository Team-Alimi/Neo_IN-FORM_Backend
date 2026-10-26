package today.inform.inform.mail;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * AWS SES(v2) 구현.
 *
 * <p><b>클라이언트를 처음 쓸 때 만듭니다.</b> 빈 생성 시점에 만들면 자격증명이 없는 환경
 * (통합 테스트·로컬 개발)에서 애플리케이션 컨텍스트 자체가 뜨지 않습니다.
 * {@code S3FileStorage} 가 같은 이유로 같은 구조를 씁니다 — 거기서 이미 겪은 문제입니다.
 *
 * <p><b>{@code MAIL_FROM} 이 비어 있으면 비활성입니다.</b> 예외로 기동을 막지 않습니다.
 * 메일은 부가 기능이고, 그것 때문에 서비스 전체가 안 뜨면 안 됩니다.
 * 대신 {@link #isEnabled()} 가 {@code false} 를 돌려주고 호출부가 발송을 건너뜁니다.
 *
 * <p><b>본문은 평문(Text)으로만 보냅니다.</b> 공지 제목이 그대로 들어가는데 HTML 로
 * 조립하면 태그·꺾쇠가 섞인 제목에서 깨지거나 주입이 됩니다.
 * 마감 알림에 서식이 필요하지도 않습니다.
 */
@Slf4j
@Component
public class SesMailSender implements MailSender {

    /** 표시 이름까지 포함한 발신자. 예: {@code INFORM <no-reply@inha-inform.today>} */
    private final String from;
    private final String region;
    private final Supplier<SesV2Client> client;

    public SesMailSender(
            @Value("${MAIL_FROM:}") String from,
            @Value("${aws.region:${AWS_REGION:ap-northeast-2}}") String region) {
        this.from = from == null ? "" : from.strip();
        this.region = region;

        // 첫 호출에서 만들고 이후 재사용합니다. 자격증명이 없으면 그 호출에서만 실패합니다.
        this.client = new Supplier<>() {
            private SesV2Client cached;

            @Override
            public synchronized SesV2Client get() {
                if (cached == null) {
                    cached = SesV2Client.builder()
                            .region(Region.of(SesMailSender.this.region))
                            .build();
                }
                return cached;
            }
        };
    }

    @Override
    public boolean isEnabled() {
        return !from.isBlank();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!isEnabled()) {
            // 호출부가 isEnabled() 를 보도록 돼 있지만, 빠뜨렸을 때 조용히 성공한 척하면 안 됩니다.
            throw new MailSendFailure("메일 발송이 비활성 상태입니다 (MAIL_FROM 미설정)", null);
        }
        try {
            client.get().sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(from)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(utf8(subject))
                                    .body(Body.builder().text(utf8(body)).build())
                                    .build())
                            .build())
                    .build());
        } catch (RuntimeException e) {
            // ★ 주소·본문을 메시지에 담지 않습니다. 예외는 그대로 올려 호출부가
            //   email_sent_at 을 찍지 않게 하고, 다음 주기에 재시도되게 합니다.
            throw new MailSendFailure("SES 발송 실패", e);
        }
    }

    /** 한글 제목·본문이 깨지지 않게 charset 을 명시합니다. 생략하면 SES 기본값에 맡겨집니다. */
    private static Content utf8(String value) {
        return Content.builder().data(value).charset("UTF-8").build();
    }
}
