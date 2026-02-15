package today.inform.inform_backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import today.inform.inform_backend.entity.VendorType;

@Getter
@NoArgsConstructor
public class BookmarkRequest {
    private VendorType articleType;
    private Integer articleId;
}
