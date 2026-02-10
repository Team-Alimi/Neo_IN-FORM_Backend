package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private final String accessToken;
    private final String refreshToken;
    private final UserInfo userInfo;
    private final boolean isNewUser;

    @Getter
    @Builder
    public static class UserInfo {
        private final Integer userId;
        private final String email;
        private final String name;
        private final Integer majorId;
    }
}
