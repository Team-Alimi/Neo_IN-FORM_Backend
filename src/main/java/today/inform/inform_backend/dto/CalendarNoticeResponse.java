package today.inform.inform_backend.dto;

import lombok.Builder;

import lombok.Getter;



import java.time.LocalDate;

import java.util.List;



@Getter
@Builder
public class CalendarNoticeResponse {
    private Integer articleId;
    private String title;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String categoryName;
    private String status;
    private Boolean isBookmarked;
    private Long bookmarkCount;
    private List<VendorListResponse> vendors;
}
