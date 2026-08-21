package today.inform.inform.category.dto.response;

import java.util.List;
import today.inform.inform.category.entity.Category;

/**
 * COM-02 분류 한 건.
 *
 * <p><b>{@code code} 를 내보내지 않습니다.</b> 크롤러 AI 분류와 주고받는 계약 키라
 * 화면이 쓸 이유가 없고, 내보내면 프론트가 그 문자열에 로직을 걸기 시작합니다.
 * 표시는 {@code name}, 식별은 {@code id} 입니다.
 */
public record CategoryResponse(Long id, String name, int sortOrder) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
    }

    public static List<CategoryResponse> from(List<Category> categories) {
        return categories.stream().map(CategoryResponse::from).toList();
    }
}
