package today.inform.inform.category.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.category.dto.response.CategoryResponse;
import today.inform.inform.category.repository.CategoryRepository;

/**
 * COM-02 분류 목록 (사용자용). {@code sort_order} 오름차순.
 *
 * <p><b>고를 수 있는 항목만 내보냅니다.</b> 이 목록이 곧 관심분야 선택 화면이고,
 * 고를 수 없는 분류는 DB 트리거가 신규 선택을 IN010 으로 거부합니다(V10·V14).
 * 여기서 걸러 내지 않으면 사용자에게 <b>고를 수 있는 것처럼 보여 주고 저장에서 400</b> 을 줍니다.
 *
 * <p><b>"활성" 과 "고를 수 있음" 은 다릅니다.</b> 기타(ETC)는 활성이지만 고를 수 없습니다 —
 * 크롤러가 "어디에도 안 맞음" 을 표시하는 데 쓰는 분류라 살아 있어야 하지만, 온보딩에서
 * 사용자에게 기타를 고르라고 할 수는 없습니다. 그래서 {@code search(true)} 가 아니라
 * {@code findSelectable()} 을 씁니다.
 * 관리자 화면({@code AdminCategoryService})은 반대로 전부 봐야 하므로 그대로 둡니다.
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findActive() {
        return CategoryResponse.from(categoryRepository.findSelectable());
    }
}
