package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ClubArticleDetailResponse {
    private Integer articleId;
    private String title;
    private String content;
    private String originalUrl;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentResponse> attachments;
    private VendorResponse vendors;

    @Getter
    @Builder
    public static class AttachmentResponse {
        private Integer fileId;
        private String fileUrl;
    }

    @Getter
    @Builder
    public static class VendorResponse {
        private Integer vendorId;
        private String vendorName;
        private String vendorInitial;
        private String vendorType;
    }
}
