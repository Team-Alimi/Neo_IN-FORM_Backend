package today.inform.inform.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** AUTH-02 / AUTH-03. 재발급과 로그아웃 모두 refresh token 으로 대상 세션을 지목합니다. */
public record RefreshTokenRequest(
        @NotBlank(message = "refresh_token 은 필수입니다.")
        String refreshToken) {
}
