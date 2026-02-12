package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ClubArticleListResponse {
    private PageInfo pageInfo;
    private List<ClubArticleResponse> clubArticles;

    @Getter
    @Builder
    public static class PageInfo {
        private Integer currentPage;
        private Integer totalPages;
        private Long totalArticles;
    }
}
