package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SchoolArticleListResponse {
    private PageInfo page_info;
    private List<SchoolArticleResponse> school_articles;

    @Getter
    @Builder
    public static class PageInfo {
        private Integer current_page;
        private Integer total_pages;
        private Long total_articles;
        private Boolean has_next;
    }
}
