package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ClubArticleDetailResponse {
    private Integer article_id;
    private String title;
    private String content;
    private String original_url;
    private LocalDate start_date;
    private LocalDate due_date;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private Boolean is_bookmarked;
    private List<AttachmentResponse> attachments;
    private VendorResponse vendors;

    @Getter
    @Builder
    public static class AttachmentResponse {
        private Integer file_id;
        private String file_url;
    }

    @Getter
    @Builder
    public static class VendorResponse {
        private Integer vendor_id;
        private String vendor_name;
        private String vendor_initial;
    }
}
