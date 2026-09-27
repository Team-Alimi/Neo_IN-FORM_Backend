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
 * 사용자에게 기타를 고르라고 할 수는 없습니다.
 *
 * <p><b>그런데 이 한 엔드포인트를 온보딩과 목록 필터가 함께 씁니다.</b>
 * 필터 쪽은 기타가 필요합니다 — 기타로 분류된 공지가 실제로 있고,
 * 사용자가 그걸 골라 볼 수 있어야 하기 때문입니다.
 * 그래서 {@code includeUnselectable} 로 용도를 가릅니다.
 * 관리자 화면({@code AdminCategoryService})은 반대로 전부 봐야 하므로 그대로 둡니다.
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    /**
     * @param includeUnselectable 고를 수 없는 분류(기타)까지 포함할지.
     *                            <b>기본은 false</b> — 빠뜨렸을 때 "덜 보이는" 쪽으로 기울게 합니다.
     *                            true 로 두고 잊으면 온보딩에 기타가 나타나 저장에서 400 이 납니다.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findActive(boolean includeUnselectable) {
        return CategoryResponse.from(includeUnselectable
                ? categoryRepository.search(true)
                : categoryRepository.findSelectable());
    }
}
