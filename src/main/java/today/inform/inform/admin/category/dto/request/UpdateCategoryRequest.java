package today.inform.inform.admin.category.dto.request;

import jakarta.validation.constraints.Size;
import today.inform.inform.category.entity.Category;

/**
 * CAT-02 카테고리 수정. 부분 수정이며 {@code null} 은 "그대로 두기" 입니다.
 *
 * @param code 바꿀 수 없습니다. 현재 값과 같으면 무시하고, 다르면 400 으로 거부합니다.
 *             명세는 "요청에 있어도 무시" 지만, 실제로 <b>다른</b> 값을 보낸 요청까지 조용히 넘기면
 *             관리자는 크롤러 연동 키가 바뀐 줄 알게 됩니다. 폼이 되돌려 보내는 같은 값만 무시합니다
 */
public record UpdateCategoryRequest(
        @Size(max = Category.NAME_MAX_LENGTH, message = "카테고리 이름은 100자를 넘을 수 없습니다.")
        String name,

        Integer sortOrder,

        Boolean isActive,

        @Size(max = Category.CODE_MAX_LENGTH, message = "카테고리 코드는 50자를 넘을 수 없습니다.")
        String code) {
}
