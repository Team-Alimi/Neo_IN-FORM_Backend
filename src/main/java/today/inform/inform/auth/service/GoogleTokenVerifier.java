package today.inform.inform.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 구글 ID Token 을 검증하고 이메일·이름을 꺼냅니다.
 *
 * <p>{@link GoogleIdTokenVerifier} 가 서명·발급자·audience·만료를 모두 확인합니다.
 * 직접 디코딩해서 이메일만 읽으면 <b>아무나 남의 이메일로 로그인할 수 있습니다.</b>
 *
 * <p>인하대 도메인 제한도 여기서 겁니다. 자동 가입 경로이므로
 * 이 검사를 통과한 이메일은 곧바로 계정이 됩니다.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    /** 학교 계정만 가입할 수 있습니다. 대학원·교직원 계정을 위해 두 도메인을 모두 허용합니다. */
    private static final Set<String> ALLOWED_DOMAINS = Set.of("inha.edu", "inha.ac.kr");

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.oauth.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    public GoogleUser verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | java.io.IOException e) {
            log.warn("구글 토큰 검증 중 오류", e);
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
        } catch (IllegalArgumentException e) {
            // JWS 형식이 아예 아닌 문자열이면 파싱 단계에서 터집니다.
            // 잡지 않으면 400 이 아니라 500 으로 나갑니다.
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
        }

        if (idToken == null) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        // 구글이 이메일 소유를 확인하지 못한 계정은 신뢰할 수 없습니다.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "이메일이 인증되지 않은 구글 계정입니다.");
        }

        String email = payload.getEmail();
        if (email == null || !isAllowedDomain(email)) {
            throw new BusinessException(ErrorCode.DOMAIN_RESTRICTED);
        }

        Object name = payload.get("name");
        return new GoogleUser(email.toLowerCase(Locale.ROOT), name != null ? name.toString() : null);
    }

    private boolean isAllowedDomain(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        return ALLOWED_DOMAINS.contains(email.substring(at + 1).toLowerCase(Locale.ROOT));
    }

    public record GoogleUser(String email, String name) {
    }
}
