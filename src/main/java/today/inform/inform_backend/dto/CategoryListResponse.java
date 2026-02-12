package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryListResponse {
    private Integer categoryId;
    private String categoryName;
}
