package today.inform.inform.admin.category.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.category.dto.request.CreateCategoryRequest;
import today.inform.inform.admin.category.dto.request.UpdateCategoryRequest;
import today.inform.inform.admin.category.dto.response.AdminCategoryResponse;
import today.inform.inform.category.entity.Category;
import today.inform.inform.category.repository.CategoryRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * CAT-01 등록 · CAT-02 수정 · CAT-03 삭제.
 *
 * <p><b>{@code code} 는 크롤러와의 계약입니다.</b> AI 분류 결과가 이 문자열로 들어오므로
 * 양쪽 목록이 어긋나면 그 분류의 공지가 <b>분류 없이</b> 쌓입니다.
 * 오류가 아니라서 "확인 필요" 목록이 조용히 늘어나는 모양으로만 드러납니다.
 */
@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    /**
     * 비활성화가 <b>절반만</b> 듣는다는 사실을 관리자에게 알립니다.
     *
     * <p>V10 트리거가 막는 것은 사용자의 <b>신규 관심분야 선택</b>뿐입니다.
     * 공지에 붙는 분류({@code article_categories})는 크롤러가 앱을 거치지 않고 직접 쓰는 경로라
     * 여기서 막으면 AI 분류가 비활성 코드를 내보내는 순간 <b>수집이 통째로 멈춥니다.</b>
     * 그래서 제공처(D7 규약)와 같은 2단계입니다 — 앱에서 끄고, 크롤러 목록에서도 빼야 합니다.
     */
    private static final String DEACTIVATION_WARNING =
            "이미 붙어 있는 공지 분류는 그대로 유지되고, 크롤러 AI 분류 목록에서도 이 코드를 빼야 "
                    + "새 공지에 더 이상 붙지 않습니다.";

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> search(Boolean active) {
        List<Category> categories = categoryRepository.search(active);
        Set<Long> inUseIds = new HashSet<>(categoryRepository.findInUseIds());
        return AdminCategoryResponse.of(categories, inUseIds);
    }

    /**
     * CAT-01 등록.
     *
     * <p>{@code code} 와 {@code name} 이 <b>둘 다</b> UNIQUE 입니다.
     * 어느 쪽이 겹쳤는지 알려 주지 않으면 관리자는 같은 화면에서 두 값을 번갈아 바꿔 봐야 합니다.
     */
    @Transactional
    public AdminCategoryResponse create(CreateCategoryRequest request) {
        Category category = Category.create(
                request.code(), request.name(), request.sortOrderOrDefault());

        if (categoryRepository.existsByCode(category.getCode())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE, "이미 쓰이고 있는 분류 코드입니다: " + category.getCode());
        }
        requireNameAvailable(category.getName(), null);

        // 새 분류는 크롤러 AI 분류 목록에 이 코드가 추가돼야 실제로 붙기 시작합니다.
        // 시드 쪽은 이 API 의 사정 밖이라 강제할 방법이 없습니다 — 화면 안내에 맡깁니다.
        return AdminCategoryResponse.of(categoryRepository.save(category), false);
    }

    /**
     * CAT-02 수정. 표시용 값만 바뀝니다.
     *
     * <p>{@code code} 는 엔티티가 {@code updatable = false} 라 보내도 UPDATE 에 실리지 않지만,
     * <b>다른</b> 값을 보낸 경우까지 조용히 넘기면 관리자가 바뀐 줄 압니다. 명시적으로 거부합니다.
     */
    @Transactional
    public AdminCategoryResponse update(Long categoryId, UpdateCategoryRequest request) {
        Category category = load(categoryId);

        String requestedCode = normalizeCode(request.code());
        if (requestedCode != null && !requestedCode.equals(category.getCode())) {
            throw new BusinessException(
                    ErrorCode.IMMUTABLE_FIELD,
                    "분류 코드는 등록 후 바꿀 수 없습니다. 크롤러가 이 값으로 분류 결과를 보냅니다. "
                            + "바꿔야 한다면 새 분류를 만들고 기존 것은 비활성화하세요.");
        }

        if (request.name() != null) {
            requireNameAvailable(request.name().trim(), categoryId);
            category.rename(request.name());
        }
        if (request.sortOrder() != null) {
            category.changeSortOrder(request.sortOrder());
        }
        String warning = null;
        if (request.isActive() != null && request.isActive() != category.isActive()) {
            category.changeActive(request.isActive());
            warning = request.isActive() ? null : DEACTIVATION_WARNING;
        }

        return AdminCategoryResponse.of(category, categoryRepository.isInUse(categoryId), warning);
    }

    /**
     * CAT-03 삭제. <b>아무도 안 쓰는 분류만</b> 지울 수 있습니다.
     *
     * <p><b>잠그고 시작합니다.</b> 확인과 DELETE 사이에 공지가 이 분류를 달면
     * DELETE 가 23503 으로 실패하는데, 그 SQLSTATE 는 "참조 대상이 존재하지 않습니다"(400)로
     * 매핑돼 있어 <b>정반대 뜻의 메시지</b>가 나갑니다.
     * {@code FOR UPDATE} 는 자식 INSERT 가 부모 행에 잡는 {@code FOR KEY SHARE} 와 충돌하므로
     * 그 사이를 실제로 막아 줍니다({@code CategoryRepository#lockById} 참조).
     *
     * <p>운영에 들어간 분류는 사실상 전부 쓰이는 중이라 여기까지 오지 못합니다.
     * 이 기능은 <b>잘못 만든 분류를 등록 직후 되돌리는 용도</b>입니다.
     */
    @Transactional
    public void delete(Long categoryId) {
        if (categoryRepository.lockById(categoryId).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (categoryRepository.isInUse(categoryId)) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_IN_USE,
                    "이미 공지나 사용자 관심분야에 쓰이고 있어 삭제할 수 없습니다. "
                            + "대신 비활성화하면 새로 선택되지 않습니다. " + DEACTIVATION_WARNING);
        }
        categoryRepository.delete(load(categoryId));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** {@code name} 도 UNIQUE 입니다. 자기 자신과의 충돌은 제외합니다. */
    private void requireNameAvailable(String name, Long selfId) {
        categoryRepository.findByName(name)
                .filter(found -> !found.getId().equals(selfId))
                .ifPresent(found -> {
                    throw new BusinessException(
                            ErrorCode.DUPLICATE_RESOURCE, "이미 쓰이고 있는 분류 이름입니다: " + name);
                });
    }

    /** 엔티티와 같은 규칙으로 맞춥니다. 대소문자만 다른 값이 "바꾸려는 시도" 로 오해되지 않도록. */
    private static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(java.util.Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Category load(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
