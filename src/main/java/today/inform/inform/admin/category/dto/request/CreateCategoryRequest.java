package today.inform.inform.admin.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import today.inform.inform.category.entity.Category;

/**
 * CAT-01 분류 등록.
 *
 * @param code      크롤러 AI 분류 계약 키. <b>등록 후 변경 불가.</b>
 *                  크롤러가 이 문자열로 분류 결과를 보내므로 <b>양쪽 목록을 먼저 맞춰</b> 두어야 합니다.
 *                  대문자로 정규화되어 저장됩니다
 * @param sortOrder 화면 정렬. 생략하면 0 입니다
 */
public record CreateCategoryRequest(
        @NotBlank(message = "분류 코드를 입력해 주세요.")
        @Size(max = Category.CODE_MAX_LENGTH, message = "분류 코드는 50자를 넘을 수 없습니다.")
        String code,

        @NotBlank(message = "분류 이름을 입력해 주세요.")
        @Size(max = Category.NAME_MAX_LENGTH, message = "분류 이름은 100자를 넘을 수 없습니다.")
        String name,

        Integer sortOrder) {

    public int sortOrderOrDefault() {
        return sortOrder == null ? 0 : sortOrder;
    }
}
