package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.category.dto.request.CreateCategoryRequest;
import today.inform.inform.admin.category.dto.request.UpdateCategoryRequest;
import today.inform.inform.admin.category.dto.response.AdminCategoryResponse;
import today.inform.inform.admin.category.service.AdminCategoryService;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.support.IntegrationTest;

/**
 * CAT-01 ~ CAT-03 분류 관리.
 *
 * <p>여기서 가장 중요한 것은 <b>삭제가 거부되는 방식</b>입니다.
 * {@code article_categories} 와 {@code user_interest_categories} 가 둘 다 RESTRICT 라
 * 그냥 지우면 23503 이 나는데, 그 SQLSTATE 는 "참조 대상이 존재하지 않습니다"(400)로 매핑돼 있어
 * <b>정반대 뜻의 메시지</b>가 나갑니다. 관리자는 원인을 영영 못 찾습니다.
 */
@Transactional
class AdminCategoryTest extends IntegrationTest {

    @Autowired
    private AdminCategoryService categoryService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // CAT-01 등록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 분류 코드는 대문자로 고정된다 — 대소문자만 다르면 UNIQUE 에도 안 걸린다")
    void codeIsNormalizedToUpperCase() {
        AdminCategoryResponse created = create("scholarship_new", "새 장학");

        assertThat(created.code())
                .as("SCHOLARSHIP 과 Scholarship 이 둘 다 그럴듯해 보여 화면만으로는 진짜를 못 고릅니다")
                .isEqualTo("SCHOLARSHIP_NEW");
        assertThat(created.isActive()).isTrue();
        assertThat(created.inUse()).isFalse();
    }

    @Test
    @DisplayName("분류 코드에 한글이나 공백은 쓸 수 없다")
    void codeShapeIsRestricted() {
        assertThatThrownBy(() -> create("장학금", "한글 코드"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> create("TWO WORDS", "공백 코드"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("코드가 겹치면 어느 값이 문제인지 알려 준다")
    void duplicateCodeIsRejectedWithReason() {
        create("DUP_CODE", "첫 번째");
        em.flush();

        assertThatThrownBy(() -> create("dup_code", "두 번째"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DUP_CODE");
    }

    @Test
    @DisplayName("★ 이름도 UNIQUE 다 — 코드만 확인하면 저장에서 뭉뚱그린 409 를 받는다")
    void duplicateNameIsRejectedWithReason() {
        create("NAME_A", "겹치는 이름");
        em.flush();

        assertThatThrownBy(() -> create("NAME_B", "겹치는 이름"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("겹치는 이름");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CAT-02 수정
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 분류 코드를 바꾸려 하면 거부한다")
    void codeCannotBeChanged() {
        Long id = create("KEEP_CODE", "코드 고정").id();
        em.flush();

        assertThatThrownBy(() -> categoryService.update(id,
                new UpdateCategoryRequest(null, null, null, "OTHER_CODE")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMMUTABLE_FIELD);
    }

    @Test
    @DisplayName("★ 같은 코드를 소문자로 되돌려 보내도 통과한다 (DB 로 확인)")
    void sameCodeInDifferentCaseIsAccepted() {
        Long id = create("FORM_CODE", "폼 코드").id();
        em.flush();

        categoryService.update(id, new UpdateCategoryRequest("바뀐 이름", 5, null, "form_code"));

        // ★ 응답 DTO 는 방금 만진 managed 엔티티에서 값을 꺼냅니다.
        //   UPDATE 가 DB 에 안 가도 통과하므로 flush + clear 후 직접 읽습니다.
        flushAndClear();
        assertThat(column(id, "name")).isEqualTo("바뀐 이름");
        assertThat(column(id, "sort_order")).isEqualTo(5);
        assertThat(column(id, "code"))
                .as("code 는 updatable=false 라 UPDATE 문에 실리지 않아야 합니다")
                .isEqualTo("FORM_CODE");
    }

    @Test
    @DisplayName("자기 이름으로 다시 저장하는 것은 중복이 아니다")
    void renamingToItsOwnNameIsAllowed() {
        Long id = create("SELF_NAME", "그대로 이름").id();
        em.flush();

        assertThat(categoryService.update(id,
                new UpdateCategoryRequest("그대로 이름", null, null, null)).name())
                .isEqualTo("그대로 이름");
    }

    @Test
    @DisplayName("★ 쓰이는 중인 분류는 비활성화로 접는다 — 삭제가 막히는 유일한 대안이다")
    void inUseCategoryCanBeDeactivated() {
        Long id = create("RETIRE_ME", "접을 분류").id();
        link(id);
        em.flush();

        AdminCategoryResponse updated = categoryService.update(id,
                new UpdateCategoryRequest(null, null, false, null));

        assertThat(updated.isActive()).isFalse();
        assertThat(updated.inUse()).isTrue();
        assertThat(updated.warning())
                .as("비활성화는 절반만 듣습니다 — 크롤러 AI 분류 목록에서도 빼야 합니다")
                .contains("크롤러");

        flushAndClear();
        assertThat(column(id, "is_active")).isEqualTo(Boolean.FALSE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CAT-03 삭제
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("아무도 안 쓰는 분류는 지워진다")
    void unusedCategoryIsDeleted() {
        Long id = create("DELETE_ME", "지울 분류").id();
        em.flush();

        categoryService.delete(id);
        em.flush();

        assertThat(exists(id)).isFalse();
    }

    @Test
    @DisplayName("★ 공지가 쓰고 있으면 409 다 — 400 '참조 대상이 없습니다' 가 나가면 안 된다")
    void categoryUsedByArticleCannotBeDeleted() {
        Long id = create("USED_BY_ARTICLE", "공지가 쓰는 분류").id();
        link(id);
        em.flush();

        assertThatThrownBy(() -> categoryService.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .as("FK 위반은 방향을 구분하지 못합니다. 여기서 먼저 판정해야 뜻이 맞는 오류가 나갑니다")
                .isEqualTo(ErrorCode.CATEGORY_IN_USE);
    }

    @Test
    @DisplayName("★ 사용자 관심분야가 쓰고 있어도 지울 수 없다 — 한쪽만 확인하면 DELETE 에서 터진다")
    void categoryUsedByUserInterestCannotBeDeleted() {
        Long id = create("USED_BY_USER", "관심분야 분류").id();
        Long userId = insertUser("cat-interest@inha.ac.kr");
        em.createNativeQuery(
                        "INSERT INTO user_interest_categories (user_id, category_id) VALUES (:u, :c)")
                .setParameter("u", userId).setParameter("c", id).executeUpdate();
        em.flush();

        assertThatThrownBy(() -> categoryService.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_IN_USE);
    }

    @Test
    @DisplayName("없는 분류를 지우면 404")
    void deletingMissingCategoryIsNotFound() {
        assertThatThrownBy(() -> categoryService.delete(999_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("목록의 in_use 로 삭제 가능 여부를 미리 알 수 있다")
    void listMarksCategoriesThatCannotBeDeleted() {
        Long used = create("LIST_USED", "쓰이는 분류").id();
        Long free = create("LIST_FREE", "안 쓰이는 분류").id();
        link(used);
        em.flush();

        assertThat(categoryService.search(null))
                .filteredOn(row -> row.id().equals(used) || row.id().equals(free))
                .extracting(AdminCategoryResponse::id, AdminCategoryResponse::inUse)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(used, true),
                        org.assertj.core.groups.Tuple.tuple(free, false));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 영속성 컨텍스트 캐시가 아니라 DB 를 보게 만듭니다. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Object column(Long categoryId, String name) {
        return em.createNativeQuery("SELECT " + name + " FROM categories WHERE id = :id")
                .setParameter("id", categoryId).getSingleResult();
    }

    private AdminCategoryResponse create(String code, String name) {
        return categoryService.create(new CreateCategoryRequest(code, name, 0));
    }

    /** 공지 하나를 만들어 이 분류를 붙입니다. */
    private void link(Long categoryId) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle("분류 테스트 공지", "내용", null, null, null));
        em.createNativeQuery("INSERT INTO article_categories (article_id, category_id) VALUES (:a, :c)")
                .setParameter("a", article.getId()).setParameter("c", categoryId).executeUpdate();
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '분류테스터', 'USER', 'ACTIVE')")
                .setParameter("email", email).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }

    private boolean exists(Long categoryId) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM categories WHERE id = :id")
                .setParameter("id", categoryId).getSingleResult()).intValue() > 0;
    }
}
