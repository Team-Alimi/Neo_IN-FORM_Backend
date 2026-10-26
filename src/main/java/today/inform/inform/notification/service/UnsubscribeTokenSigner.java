package today.inform.inform.notification.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * NTF-05 수신거부 링크의 서명.
 *
 * <p>메일에 들어가는 링크는 <b>로그인 없이</b> 눌려야 합니다. 메일을 받은 사람이 그 자리에서
 * 끄지 못하면 수신거부가 아니고, 로그인을 요구하면 "메일이 싫어서 누른 사람" 에게
 * 가입 절차를 다시 묻는 셈입니다. 그래서 링크 자체가 권한을 들고 있어야 합니다.
 *
 * <h2>★ JWT 를 쓰지 않습니다</h2>
 * jjwt 가 이미 있지만 일부러 다른 형식입니다. 인증 토큰과 모양이 같으면
 * <b>수신거부 토큰을 Authorization 헤더에 넣어 보는 시도</b>가 가능해지고,
 * 그게 막히는 이유가 "키가 달라서" 하나에만 걸립니다.
 * 형식이 아예 다르면 인증 필터가 파싱조차 못 합니다.
 *
 * <h2>★ 키를 JWT_SECRET 과 분리합니다</h2>
 * 같은 키를 쓰면 수신거부 토큰 하나가 새어 나갔을 때 그것이 인증 서명과 같은 비밀을
 * 쓴다는 뜻이 되고, 키를 돌려야 할 때 <b>전원 로그아웃</b>이 따라옵니다.
 * 수신거부 키를 돌리는 비용은 "메일에 담긴 옛 링크가 만료되는 것" 뿐이어야 합니다.
 *
 * <h2>★ 토큰에 주소를 담지 않습니다</h2>
 * 담는 것은 사용자 id 입니다. 주소를 담으면 링크 자체가 개인정보가 되어
 * 메일 전달·브라우저 히스토리·웹서버 접근 로그에 그대로 남습니다.
 *
 * <h2>형식</h2>
 * <pre>
 * &lt;userId&gt;.&lt;만료 epoch 초&gt;.&lt;base64url(HMAC-SHA256)&gt;
 * </pre>
 *
 * <p>{@code UNSUBSCRIBE_SECRET} 이 없으면 <b>비활성</b>입니다. 예외로 기동을 막지 않고
 * ({@code SesMailSender} 와 같은 판단), 대신 메일에서 수신거부 문단이 빠집니다 —
 * 누를 수 없는 링크를 메일에 넣는 것보다 낫습니다.
 */
@Slf4j
@Component
public class UnsubscribeTokenSigner {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 링크 유효 기간.
     *
     * <p><b>설정값으로 빼지 않았습니다.</b> 짧게 줄이면 "메일함에 있는 수신거부 링크가
     * 안 먹는" 상태가 되는데, 그건 사용자가 메일을 못 끄게 막는 것이라
     * 운영에서 조정할 성격의 값이 아닙니다. 알림 자체가 30일 뒤 삭제되므로(SYS-04)
     * 90일은 실제로 만료된 링크를 누를 일이 드물 만큼 넉넉합니다.
     *
     * <p>반대로 무기한으로 두지 않는 이유 — 메일은 지워지지 않고 메일함에 남습니다.
     * 만료가 없으면 한 번 새어 나간 링크가 영구히 "이 사람 메일 끄기" 버튼이 됩니다.
     */
    private static final Duration VALIDITY = Duration.ofDays(90);

    /** 비밀이 없으면 길이 0. {@link #isEnabled()} 가 그것을 봅니다. */
    private final byte[] secret;

    public UnsubscribeTokenSigner(@Value("${UNSUBSCRIBE_SECRET:}") String secret) {
        String trimmed = secret == null ? "" : secret.strip();
        if (trimmed.isEmpty()) {
            log.warn("UNSUBSCRIBE_SECRET 이 없습니다. 메일에 수신거부 링크가 들어가지 않습니다");
            this.secret = new byte[0];
            return;
        }
        if (trimmed.length() < 32) {
            // 짧은 비밀은 조용히 받아들이면 안 됩니다. 서명이 붙어 있다는 사실만으로
            // 안전해 보이지만, 32자 미만은 오프라인 추측이 현실적입니다.
            throw new IllegalArgumentException("UNSUBSCRIBE_SECRET 은 32자 이상이어야 합니다");
        }
        this.secret = trimmed.getBytes(StandardCharsets.UTF_8);
    }

    /** 비밀이 설정돼 있는지. {@code false} 면 메일에 수신거부 링크를 넣지 않습니다. */
    public boolean isEnabled() {
        return secret.length > 0;
    }

    /**
     * 토큰 발급.
     *
     * @throws IllegalStateException 비활성 상태에서 호출된 경우.
     *                               호출부가 {@link #isEnabled()} 를 보도록 돼 있지만,
     *                               빠뜨렸을 때 서명 없는 토큰을 만들어 주면 안 됩니다
     */
    public String sign(Long userId) {
        if (!isEnabled()) {
            throw new IllegalStateException("UNSUBSCRIBE_SECRET 이 없어 토큰을 발급할 수 없습니다");
        }
        long expiresAt = Instant.now().plus(VALIDITY).getEpochSecond();
        String payload = payload(userId, expiresAt);
        return payload + "." + encode(mac(payload));
    }

    /**
     * 토큰 검증.
     *
     * <p><b>실패 이유를 구분해 돌려주지 않습니다.</b> 만료와 위조를 나눠 알려 주면
     * 토큰을 바꿔 가며 찔러 보는 쪽에 "형식은 맞다 / 서명은 맞다" 를 알려 주게 됩니다.
     * 사용자에게 보여 줄 문구는 어차피 하나입니다 —
     * "링크가 만료되었거나 올바르지 않습니다. 설정에서 끄실 수 있습니다."
     *
     * @return 유효하면 사용자 id. 만료·위조·형식 오류는 모두 빈 값
     */
    public Optional<Long> verify(String token) {
        if (!isEnabled() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }
        String payload = token.substring(0, lastDot);
        String signature = token.substring(lastDot + 1);

        byte[] presented;
        try {
            presented = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // ★ equals 가 아니라 isEqual 입니다. 바이트 단위로 먼저 어긋나는 지점에서
        //   빠져나오는 비교는 걸린 시간으로 서명을 한 바이트씩 맞춰 갈 여지를 줍니다.
        if (!MessageDigest.isEqual(mac(payload), presented)) {
            return Optional.empty();
        }

        // 서명이 맞은 뒤에 payload 를 해석합니다. 순서가 중요합니다 —
        // 먼저 파싱하면 서명되지 않은 입력으로 파서를 두드릴 수 있습니다.
        int separator = payload.indexOf('.');
        if (separator < 0) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(payload.substring(0, separator));
            long expiresAt = Long.parseLong(payload.substring(separator + 1));
            if (Instant.now().getEpochSecond() > expiresAt) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String payload(Long userId, long expiresAt) {
        return userId + "." + expiresAt;
    }

    private byte[] mac(String payload) {
        try {
            Mac hmac = Mac.getInstance(ALGORITHM);
            hmac.init(new SecretKeySpec(secret, ALGORITHM));
            return hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 은 모든 JDK 에 있습니다. 여기 오면 설정이 아니라 런타임 문제입니다.
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
