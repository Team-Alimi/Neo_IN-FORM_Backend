package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ClubArticleResponse {
    private Integer articleId;
    private String title;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String attachmentUrl;
    private VendorResponse vendors;

    @Getter
    @Builder
    public static class VendorResponse {
        private Integer vendorId;
        private String vendorName;
        private String vendorInitial;
        private String vendorType;
    }
}
