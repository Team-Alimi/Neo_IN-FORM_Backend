package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.article.service.AdminArticleService;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.support.IntegrationTest;

/**
 * ADM-03a 재검수 완료 · SYS-07 요약 재생성.
 *
 * <h2>명세가 말하는 컬럼은 이제 없습니다</h2>
 * 명세는 {@code review_requested_at} 를 지우라고 하지만 그 컬럼은 스키마에서 제거됐습니다
 * ({@code SCHEMA_STATUS} 3장 2번). 재검수 정책이 "노출 유지 + 플래그" 에서
 * <b>"검수 대기로 강등"</b> 으로 바뀌었기 때문입니다.
 * 그래서 재검수 대기 = {@code PENDING_REVIEW 이면서 published_at 이 있는} 상태입니다.
 *
 * <p><b>{@code @Transactional} 이 없습니다.</b> 벌크는 건별로 커밋하므로
 * 테스트 트랜잭션 안에서는 그 새 트랜잭션이 준비 데이터를 보지 못합니다
 * ({@code BulkExecutor} 주석 참조).
 */
class AdminReviewCompleteTest extends IntegrationTest {

    @Autowired
    private AdminArticleService adminArticleService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    /** 이 테스트가 만든 공지. 롤백이 없으므로 직접 지웁니다. */
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            if (!created.isEmpty()) {
                em.createNativeQuery("DELETE FROM articles WHERE id IN (:ids)")
                        .setParameter("ids", created).executeUpdate();
            }
        });
        created.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-03a
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 재검수를 통과하면 다시 배포된다 — 그 사이 피드에서 사라져 있기 때문이다")
    void completingReviewRepublishes() {
        Long article = demotedForReview("재검수 대기 공지");

        assertThat(adminArticleService.completeReview(List.of(article), "원본 수정 확인함").succeeded())
                .containsExactly(article);

        assertThat(statusOf(article))
                .as("발행 대기에 두면 누군가 한 번 더 누를 때까지 공백이 계속됩니다")
                .isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("★ 한 번도 배포된 적 없는 공지는 거부한다 — 그건 최초 검수다")
    void neverPublishedArticleIsRejected() {
        Long fresh = save("신규 수집분");

        assertThat(adminArticleService.completeReview(List.of(fresh), null).failed())
                .as("통과시키면 '재검수 완료' 버튼 하나로 검수 안 된 신규 공지가 바로 배포됩니다")
                .singleElement()
                .satisfies(failure ->
                        assertThat(failure.code()).isEqualTo("INVALID_STATE_TRANSITION"));

        assertThat(statusOf(fresh)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("이미 배포 중인 공지도 거부한다 — 재검수 대기가 아니다")
    void alreadyPublishedArticleIsRejected() {
        Long published = publish("배포 중인 공지");

        assertThat(adminArticleService.completeReview(List.of(published), null).failed())
                .singleElement()
                .satisfies(failure ->
                        assertThat(failure.code()).isEqualTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("★ 감사 이력에 두 단계가 그대로 남는다 — 실제로 일어난 일이 두 단계다")
    void bothTransitionsAreAudited() {
        Long article = demotedForReview("이력 확인 공지");
        int before = statusLogCount(article);

        adminArticleService.completeReview(List.of(article), "확인 완료");

        assertThat(statusLogCount(article) - before)
                .as("PENDING_REVIEW → READY_TO_PUBLISH → PUBLISHED 를 순서대로 밟습니다. "
                        + "더티 체킹이 둘을 한 UPDATE 로 합치면 전이 표에 없는 점프 하나만 남습니다")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("★ 대상이 아닌 건만 실패하고 나머지는 처리된다 (부분 성공)")
    void invalidTargetsFailIndividually() {
        Long valid = demotedForReview("정상 대상");
        Long invalid = publish("배포 중인 공지");

        BulkResult result = adminArticleService.completeReview(List.of(valid, invalid), null);

        assertThat(result.succeeded()).containsExactly(valid);
        assertThat(result.failed()).extracting(BulkResult.Failure::id).containsExactly(invalid);
        assertThat(statusOf(valid))
                .as("한 건이 막혔다고 나머지를 되돌리면 관리자는 무엇이 문제인지 목록에서 찾아야 합니다")
                .isEqualTo("PUBLISHED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SYS-07
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 요약을 지워도 수정 시각과 version 은 그대로다 — 공지가 수정된 것이 아니다")
    void regeneratingSummaryDoesNotLookLikeAnEdit() {
        Long articleId = publish("요약 있는 공지");
        tx.executeWithoutResult(status ->
                em.createNativeQuery("UPDATE articles SET summary = '기존 요약' WHERE id = :id")
                        .setParameter("id", articleId).executeUpdate());

        Object updatedBefore = column(articleId, "updated_at");
        Object versionBefore = column(articleId, "version");

        adminArticleService.regenerateSummary(articleId);

        assertThat(column(articleId, "summary"))
                .as("생성은 배치가 맡습니다. 여기서는 지우기만 합니다")
                .isNull();
        assertThat(column(articleId, "updated_at"))
                .as("updated_at 화이트리스트에 summary 가 없어야 성립합니다")
                .isEqualTo(updatedBefore);
        assertThat(column(articleId, "version"))
                .as("version 이 오르면 열어 둔 관리자 화면이 저장에서 409 를 받습니다")
                .isEqualTo(versionBefore);
    }

    @Test
    @DisplayName("없는 공지면 404")
    void regeneratingMissingArticleIsNotFound() {
        assertThatThrownBy(() -> adminArticleService.regenerateSummary(999_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTICLE_NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 배포됐다가 크롤러의 원본 수정 감지로 검수 대기로 내려간 상태. */
    private Long demotedForReview(String title) {
        Long id = publish(title);
        mutate(id, article -> article.changeStatus(ArticleStatus.PENDING_REVIEW));
        return id;
    }

    private Long publish(String title) {
        Long id = save(title);
        mutate(id, article -> {
            article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
            article.changeStatus(ArticleStatus.PUBLISHED);
        });
        return id;
    }

    /** <b>커밋합니다</b> — 벌크의 새 트랜잭션이 볼 수 있어야 합니다. */
    private Long save(String title) {
        Long id = tx.execute(status -> articleRepository
                .save(Article.createSchoolArticle(title, "내용", null, null, null)).getId());
        created.add(id);
        return id;
    }

    private void mutate(Long articleId, java.util.function.Consumer<Article> change) {
        tx.executeWithoutResult(status ->
                change.accept(articleRepository.findById(articleId).orElseThrow()));
    }

    private String statusOf(Long articleId) {
        return (String) column(articleId, "status");
    }

    private Object column(Long articleId, String name) {
        return tx.execute(status ->
                em.createNativeQuery("SELECT " + name + " FROM articles WHERE id = :id")
                        .setParameter("id", articleId).getSingleResult());
    }

    private int statusLogCount(Long articleId) {
        return tx.execute(status -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM article_status_logs WHERE article_id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue());
    }
}
