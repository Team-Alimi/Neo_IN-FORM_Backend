package today.inform.inform.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** AUTH-01. 클라이언트가 구글에서 받은 ID Token 을 그대로 보냅니다. */
public record GoogleLoginRequest(
        @NotBlank(message = "id_token 은 필수입니다.")
        String idToken) {
}
