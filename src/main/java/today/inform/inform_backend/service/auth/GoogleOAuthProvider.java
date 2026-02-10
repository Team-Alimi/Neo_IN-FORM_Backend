package today.inform.inform_backend.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import today.inform.inform_backend.dto.GoogleUserInfo;
import today.inform.inform_backend.entity.SocialType;
import today.inform.inform_backend.entity.User;

import java.util.Collections;

@Component
public class GoogleOAuthProvider implements OAuthProvider {

    @Value("${google.client-id}")
    private String clientId;

    @Override
    public SocialType getSocialType() {
        return SocialType.GOOGLE;
    }

    @Override
    public GoogleUserInfo verifyToken(String idToken) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();

        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다.");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            
            User.validateInhaDomain(email);

            return GoogleUserInfo.builder()
                    .email(email)
                    .name((String) payload.get("name"))
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("구글 토큰 검증 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
