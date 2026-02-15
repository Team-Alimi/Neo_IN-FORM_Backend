package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SchoolArticleResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer articleId;
    private String title;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isBookmarked;
    private Long bookmarkCount;
    private List<VendorResponse> vendors;
    private CategoryResponse categories;

    @Getter
    @Builder
    public static class VendorResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer vendorId;
        private String vendorName;
        private String vendorInitial;
        private String vendorType;
    }

    @Getter
    @Builder
    public static class CategoryResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer categoryId;
        private String categoryName;
    }
}
