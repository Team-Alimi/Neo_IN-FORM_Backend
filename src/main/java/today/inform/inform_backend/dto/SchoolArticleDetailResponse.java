package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SchoolArticleDetailResponse {
    private Integer article_id;
    private String title;
    private String content;
    private LocalDate start_date;
    private LocalDate due_date;
    private String status;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private List<VendorResponse> vendors;
    private CategoryResponse categories;
    private List<AttachmentResponse> attachments;

    @Getter
    @Builder
    public static class VendorResponse {
        private String vendor_name;
        private String vendor_initial;
        private String vendor_type;
        private String original_url;
    }

    @Getter
    @Builder
    public static class CategoryResponse {
        private Integer category_id;
        private String category_name;
    }

    @Getter
    @Builder
    public static class AttachmentResponse {
        private Integer file_id;
        private String attachment_url;
    }
}
