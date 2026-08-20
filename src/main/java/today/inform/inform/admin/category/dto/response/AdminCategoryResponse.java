package today.inform.inform.admin.category.dto.response;

import java.util.List;
import java.util.Set;
import today.inform.inform.category.entity.Category;

/**
 * 관리자 분류 한 건.
 *
 * @param inUse   공지나 사용자 관심분야가 이 분류를 쓰고 있는지.
 *                {@code true} 면 삭제(CAT-03)가 거부됩니다 — 화면은 삭제 대신 비활성화를 안내해야 합니다
 * @param warning 관리자가 알아야 할 주의. 없으면 {@code null} 입니다.
 *                오류가 아니라 상태 코드로는 전달할 수 없는데, 놓치면 조작이 절반만 듣는 종류의 정보라
 *                응답에 실어 보냅니다 (제공처의 {@code AdminVendorResponse} 와 같은 방식)
 */
public record AdminCategoryResponse(
        Long id,
        String code,
        String name,
        boolean isActive,
        int sortOrder,
        boolean inUse,
        String warning) {

    public static AdminCategoryResponse of(Category category, boolean inUse) {
        return of(category, inUse, null);
    }

    public static AdminCategoryResponse of(Category category, boolean inUse, String warning) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.isActive(),
                category.getSortOrder(),
                inUse,
                warning);
    }

    public static List<AdminCategoryResponse> of(List<Category> categories, Set<Long> inUseIds) {
        return categories.stream()
                .map(category -> of(category, inUseIds.contains(category.getId())))
                .toList();
    }
}
