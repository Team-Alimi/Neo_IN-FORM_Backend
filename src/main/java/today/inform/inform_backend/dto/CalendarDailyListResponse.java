package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class CalendarDailyListResponse {
    private PageInfo pageInfo;
    private List<NoticeResponse> notices;

    @Getter
    @Builder
    public static class PageInfo {
        private Integer currentPage;
        private Integer totalPages;
        private Long totalArticles;
        private Boolean hasNext;
    }

    @Getter
    @Builder
    public static class NoticeResponse {
        private Integer articleId;
        private String title;
        private String status;
        private List<VendorListResponse> vendors;
        private LocalDate startDate;
        private String categoryName;
    }
}
