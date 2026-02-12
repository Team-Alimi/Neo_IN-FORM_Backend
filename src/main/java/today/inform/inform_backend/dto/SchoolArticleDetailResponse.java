package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SchoolArticleDetailResponse {
    private Integer articleId;
    private String title;
    private String content;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isBookmarked;
    private Long bookmarkCount;
    private List<VendorResponse> vendors;
    private CategoryResponse categories;
    private List<AttachmentResponse> attachments;

    @Getter
    @Builder
    public static class VendorResponse {
        private Integer vendorId;
        private String vendorName;
        private String vendorInitial;
        private String vendorType;
        private String originalUrl;
    }

    @Getter
    @Builder
    public static class CategoryResponse {
        private Integer categoryId;
        private String categoryName;
    }

    @Getter
    @Builder
    public static class AttachmentResponse {
        private Integer fileId;
        private String attachmentUrl;
    }
}
