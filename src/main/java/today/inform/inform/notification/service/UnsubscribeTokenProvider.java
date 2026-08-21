package today.inform.inform.notification.service;

import io.jsonwebtoken.io.Decoders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * USER-04 수신거부 링크의 서명 토큰.
 *
 * <h2>왜 JWT 가 아닌가</h2>
 * 메일 하단 링크는 <b>비로그인 클릭</b>입니다. 로그인 세션이 없으니 Access Token 을 쓸 수 없고,
 * 그렇다고 링크에 {@code user_id} 만 실으면 <b>남의 번호를 바꿔 넣어 타인의 수신을 끌 수 있습니다.</b>
 * 그래서 서버만 만들 수 있는 HMAC 서명을 붙입니다.
 *
 * <h2>왜 JWT 키를 그대로 쓰지 않는가</h2>
 * 같은 키를 서로 다른 용도에 재사용하면, 한쪽의 토큰을 다른 쪽에 밀어 넣는 공격 표면이 생깁니다.
 * 그렇다고 환경변수를 하나 더 만들면 <b>배포에서 빠뜨렸을 때 조용히 약해집니다.</b>
 * JWT 비밀에서 고정 라벨로 <b>파생</b>해 두 문제를 함께 피합니다 —
 * 설정은 늘어나지 않고 키는 분리됩니다.
 *
 * <h2>토큰에 담기는 것</h2>
 * {@code base64url(userId:만료시각) + "." + base64url(HMAC-SHA256)}.
 * 만료시각을 <b>서명 안에</b> 넣는 것이 요점입니다. 밖에 두면 클릭하는 쪽이 늘려 쓸 수 있습니다.
 *
 * <h2>남는 위험</h2>
 * 토큰이 URL 쿼리에 실리므로 <b>서버 접근 로그와 중간 프록시에 그대로 남습니다.</b>
 * 유출되어도 할 수 있는 일이 "그 사람의 메일 수신 끄기" 하나뿐이고 재구독은 로그인 후
 * {@code PATCH /users/me} 로 가능하지만, 그래서 유효기간을 짧게 둡니다.
 */
@Slf4j
@Component
public class UnsubscribeTokenProvider {

    private static final String ALGORITHM = "HmacSHA256";

    /** 키 파생 라벨. <b>바꾸면 이미 발송된 메일의 링크가 전부 무효가 됩니다.</b> */
    private static final byte[] KEY_LABEL =
            "inform:unsubscribe:v1".getBytes(StandardCharsets.US_ASCII);

    /**
     * 유효기간.
     *
     * <p>마감 D-1 메일을 며칠 뒤에 열어 보는 사람이 있으므로 짧게 두면 링크가 죽습니다.
     * 반대로 무한이면 유출된 링크가 영원히 삽니다. 메일함에서 되짚어 볼 만한 기간으로 잡습니다.
     */
    private static final Duration VALIDITY = Duration.ofDays(30);

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;

    public UnsubscribeTokenProvider(@Value("${jwt.secret}") String jwtSecret) {
        this.key = new SecretKeySpec(derive(Decoders.BASE64.decode(jwtSecret)), ALGORITHM);
    }

    /** 메일 발송(NTF-05)이 링크를 만들 때 부릅니다. */
    public String issue(Long userId) {
        String payload = userId + ":" + Instant.now().plus(VALIDITY).getEpochSecond();
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + ENCODER.encodeToString(sign(encoded));
    }

    /**
     * 서명과 만료를 확인하고 사용자 번호를 꺼냅니다.
     *
     * <p><b>실패 사유를 구분해 알려 주지 않습니다.</b> "서명이 틀렸다" 와 "만료됐다" 를 나누면
     * 위조를 시도하는 쪽에 서명이 맞았는지를 알려 주는 셈입니다.
     *
     * @throws BusinessException 서명 불일치·만료·형식 오류 모두 400
     */
    public Long parse(String token) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw invalid();
        }

        String encoded = token.substring(0, dot);
        byte[] presented = decode(token.substring(dot + 1));

        // ★ 반드시 상수 시간 비교여야 합니다. equals 로 비교하면 앞에서부터 몇 바이트가 맞았는지가
        //   응답 시간 차이로 새어 나가, 서명을 한 바이트씩 맞춰 갈 수 있습니다.
        if (!MessageDigest.isEqual(sign(encoded), presented)) {
            throw invalid();
        }

        String payload = new String(decode(encoded), StandardCharsets.UTF_8);
        int colon = payload.indexOf(':');
        if (colon <= 0) {
            throw invalid();
        }
        try {
            long expiresAt = Long.parseLong(payload.substring(colon + 1));
            if (Instant.now().getEpochSecond() > expiresAt) {
                throw invalid();
            }
            return Long.parseLong(payload.substring(0, colon));
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * JWT 비밀에서 이 용도 전용 키를 만듭니다.
     *
     * <p>라벨을 메시지로 한 HMAC 한 번. 결과에서 원래 비밀을 되돌릴 수 없고,
     * 라벨이 다르면 완전히 다른 키가 나옵니다.
     */
    private static byte[] derive(byte[] jwtSecret) {
        return hmac(new SecretKeySpec(jwtSecret, ALGORITHM), KEY_LABEL);
    }

    private byte[] sign(String encodedPayload) {
        return hmac(key, encodedPayload.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] hmac(SecretKeySpec key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(message);
        } catch (java.security.GeneralSecurityException e) {
            // 알고리즘은 JDK 표준이고 키도 우리가 만든 것이라 정상 경로에서는 오지 않습니다.
            throw new IllegalStateException("수신거부 토큰 서명에 실패했습니다.", e);
        }
    }

    private static byte[] decode(String value) {
        try {
            return DECODER.decode(value);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE, "유효하지 않거나 만료된 링크입니다. 앱에서 알림 설정을 바꿔 주세요.");
    }
}
