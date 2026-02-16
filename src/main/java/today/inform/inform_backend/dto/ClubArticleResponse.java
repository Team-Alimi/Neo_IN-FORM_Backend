package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ClubArticleResponse {
    private Integer articleId;
    private String title;
    private LocalDate startDate;
    private LocalDate dueDate;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime updatedAt;

    private String attachmentUrl;
    private List<VendorListResponse> vendors;
}
