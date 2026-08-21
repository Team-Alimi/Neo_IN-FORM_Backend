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
 * <p><b>활성 항목만 내보냅니다.</b> 이 목록이 곧 관심분야 선택 화면이고,
 * 비활성 분류는 DB 트리거가 신규 선택을 IN010 으로 거부합니다(V10).
 * 여기서 걸러 내지 않으면 사용자에게 <b>고를 수 있는 것처럼 보여 주고 저장에서 400</b> 을 줍니다.
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findActive() {
        return CategoryResponse.from(categoryRepository.search(true));
    }
}
