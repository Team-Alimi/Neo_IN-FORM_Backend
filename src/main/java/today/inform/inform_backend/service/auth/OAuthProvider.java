package today.inform.inform_backend.service.auth;

import today.inform.inform_backend.dto.GoogleUserInfo;
import today.inform.inform_backend.entity.SocialType;

public interface OAuthProvider {
    SocialType getSocialType();
    GoogleUserInfo verifyToken(String token);
}
