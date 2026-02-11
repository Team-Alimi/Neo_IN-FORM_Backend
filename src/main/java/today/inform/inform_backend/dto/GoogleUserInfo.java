package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleUserInfo {
    private final String email;
    private final String name;
}
