package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SandboxArticleListResponse {
    private List<SandboxArticleResponse> articles;
}
