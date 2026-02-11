package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ClubArticleResponse {
    private Integer article_id;
    private String title;
    private LocalDate start_date;
    private LocalDate due_date;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private String attachment_url;
    private VendorResponse vendors;

    @Getter
    @Builder
    public static class VendorResponse {
        private String vendor_name;
        private String vendor_initial;
    }
}
