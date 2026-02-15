package today.inform.inform_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenRefreshResponse {
    private String accessToken;
    private String refreshToken;
}
