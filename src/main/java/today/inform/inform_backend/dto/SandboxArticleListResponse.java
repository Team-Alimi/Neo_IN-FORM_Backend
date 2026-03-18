package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SandboxArticleListResponse {
    private PageInfo pageInfo;
    private List<SandboxArticleResponse> sandboxArticles;

    @Getter
    @Builder
    public static class PageInfo {
        private Integer currentPage;
        private Integer totalPages;
        private Long totalArticles;
        private Boolean hasNext;
    }
}
