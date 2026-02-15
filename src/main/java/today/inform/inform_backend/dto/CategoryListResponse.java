package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class CategoryListResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer categoryId;
    private String categoryName;
}
