package today.inform.inform_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminArticleUpdateRequest {

    private String title;
    private String content;
    private Integer categoryId;
    private LocalDate startDate;
    private LocalDate dueDate;
    
    private List<VendorRequest> vendors;
    private List<String> attachmentUrls;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorRequest {
        private Integer vendorId;
        private String originalUrl;
    }
}
