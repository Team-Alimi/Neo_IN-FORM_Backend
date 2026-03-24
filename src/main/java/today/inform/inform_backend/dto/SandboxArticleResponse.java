package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SandboxArticleResponse {
    private Integer sandboxId;
    private String title;
    private String adminStatus;
    private String previousStatus;
    private LocalDate startDate;
    private LocalDate dueDate;
    
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;
    
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime updatedAt;
    
    private List<VendorResponse> vendors;
    private CategoryResponse categories;

    @Getter
    @Builder
    public static class VendorResponse {
        private Integer vendorId;
        private String vendorName;
        private String vendorInitial;
        private String vendorType;
    }

    @Getter
    @Builder
    public static class CategoryResponse {
        private Integer categoryId;
        private String categoryName;
    }
}
