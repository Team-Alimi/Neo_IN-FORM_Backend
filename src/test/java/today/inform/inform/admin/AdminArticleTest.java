package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.article.dto.response.ReviewStats;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.article.service.AdminArticleService;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.support.IntegrationTest;

/**
 * ADM-02 · 03 · 07 · 08 · 09 · 11 — 검수 파이프라인.
 *
 * <p>핵심은 <b>상태 전이가 앱에서 막히는가</b>와 <b>감사 이력이 빠짐없이 남는가</b>입니다.
 * 전자는 DB 가 검사하지 않고(집합만 봅니다), 후자는 앱이 하지 않습니다(트리거가 씁니다).
 * 둘 다 한쪽만 보면 확인되지 않습니다.
 *
 * <p><b>{@code @Transactional} 이 없습니다.</b> 벌크 작업은 부분 성공이라
 * <b>건별로 커밋</b>합니다({@code BulkExecutor}). 테스트 트랜잭션 안에서는 그 새 트랜잭션이
 * 커밋되지 않은 준비 데이터를 보지 못해, 전부 "공지 없음" 으로 실패합니다.
 * 부분 커밋은 실제로 커밋하는 테스트로만 확인할 수 있습니다.
 *
 * <p>그래서 만든 공지를 {@link #created} 에 모아 두고 {@code @AfterEach} 에서 지웁니다.
 */
class AdminArticleTest extends IntegrationTest {

    @Autowired
    private AdminArticleService adminArticleService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate tx;

    /** 이 테스트가 만든 공지. 롤백이 없으므로 직접 지웁니다. */
    private final List<Long> created = new java.util.ArrayList<>();

    private Long vendorId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        cleanUp();
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO vendors (name, initial, type) "
                            + "VALUES ('관리자테스트학과', 'ADMTEST', 'SCHOOL')")
                    .executeUpdate();
        });
        vendorId = scalar("SELECT id FROM vendors WHERE initial = 'ADMTEST'");
        categoryId = scalar("SELECT id FROM categories ORDER BY id LIMIT 1");
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            if (!created.isEmpty()) {
                em.createNativeQuery("DELETE FROM articles WHERE id IN (:ids)")
                        .setParameter("ids", created).executeUpdate();
            }
            em.createNativeQuery("DELETE FROM vendors WHERE initial = 'ADMTEST'").executeUpdate();
        });
        created.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-07 상태 전이
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 검수를 건너뛴 건만 실패하고 나머지는 처리된다 — 부분 성공(명세 4.8)")
    void skippingReviewFailsOnlyThatItem() {
        Long ok = at("정상 검수 대기", ArticleStatus.READY_TO_PUBLISH);
        Long skipping = pending("건너뛰려는 공지");

        BulkResult result = adminArticleService.changeStatus(
                List.of(ok, skipping), ArticleStatus.PUBLISHED, "일괄 발행");

        assertThat(result.succeeded()).containsExactly(ok);
        assertThat(result.failed())
                .as("어느 것이 왜 막혔는지 그 자리에서 알 수 있어야 합니다")
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.id()).isEqualTo(skipping);
                    assertThat(failure.code()).isEqualTo("INVALID_STATE_TRANSITION");
                });

        assertThat(statusOf(ok))
                .as("성공한 건은 그대로 남아야 합니다 — 한 건 때문에 되돌리지 않습니다")
                .isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(statusOf(skipping)).isEqualTo(ArticleStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("정상 경로는 일괄로 처리된다")
    void bulkTransitionSucceeds() {
        Long first = pending("공지 1");
        Long second = pending("공지 2");

        BulkResult result = adminArticleService.changeStatus(
                List.of(first, second), ArticleStatus.READY_TO_PUBLISH, "일괄 검수");

        assertThat(result.succeeded()).containsExactly(first, second);
        assertThat(result.failed()).isEmpty();
        assertThat(statusOf(first)).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("★ 없는 공지 번호는 그 건만 실패로 기록된다 — 조용히 건너뛰지 않는다")
    void missingArticleIsReportedNotSkipped() {
        Long article = pending("있는 공지");

        BulkResult result = adminArticleService.changeStatus(
                List.of(article, 999_999L), ArticleStatus.READY_TO_PUBLISH, null);

        assertThat(result.succeeded()).containsExactly(article);
        assertThat(result.failed())
                .as("응답에서 빠지면 관리자는 30건을 골랐는데 29건만 처리된 것을 모릅니다")
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.id()).isEqualTo(999_999L);
                    assertThat(failure.code()).isEqualTo("ARTICLE_NOT_FOUND");
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 감사 이력
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 상태를 바꾸면 사유가 이력에 남는다")
    void memoIsRecorded() {
        Long article = pending("사유 기록 대상");

        adminArticleService.changeStatus(
                List.of(article), ArticleStatus.READY_TO_PUBLISH, "오탈자 확인 완료");

        List<StatusLogResponse> logs = adminArticleService.statusLogs(article);

        assertThat(logs).hasSize(2);   // 생성(NULL→PENDING_REVIEW) + 이번 변경
        StatusLogResponse latest = logs.get(0);
        assertThat(latest.fromStatus()).isEqualTo(ArticleStatus.PENDING_REVIEW);
        assertThat(latest.toStatus()).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
        assertThat(latest.memo())
                .as("트리거가 GUC 에서 읽어 갑니다. 상태 변경 전에 넣지 않으면 NULL 이 됩니다")
                .isEqualTo("오탈자 확인 완료");
        assertThat(latest.createdAt())
                .as("타입 선언 없이 읽으면 캐스팅이 조용히 실패해 응답에서 시각이 빠집니다")
                .isNotNull();
        assertThat(latest.changedBy())
                .as("""
                        서비스를 직접 부르면 SecurityContext 가 없어 행위자가 NULL 입니다.
                        스키마상 NULL 은 "크롤러/시스템이 한 변경" 을 뜻하므로,
                        관리자 요청에서 NULL 이 되면 안 됩니다.
                        실제 HTTP 경로에서 채워지는지는 AdminAuditTest 가 확인합니다.""")
                .isNull();
    }

    @Test
    @DisplayName("사유 없이 바꿔도 이력은 남는다")
    void logIsRecordedWithoutMemo() {
        Long article = pending("사유 없는 변경");

        adminArticleService.changeStatus(List.of(article), ArticleStatus.READY_TO_PUBLISH, null);

        assertThat(adminArticleService.statusLogs(article).get(0).memo()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-08 / ADM-09 휴지통
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 휴지통 목록은 들어가기 직전 상태를 보여준다")
    void trashListShowsPreviousStatus() {
        Long article = at("휴지통 갈 공지", ArticleStatus.READY_TO_PUBLISH);

        adminArticleService.moveToTrash(List.of(article), "중복");

        AdminArticleSummary trashed = adminArticleService
                .listTrashed(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(trashed.status()).isEqualTo(ArticleStatus.TRASHED);
        assertThat(trashed.previousStatus())
                .as("articles.status 는 TRASHED 로 덮여 있어 이력에서 가져와야 합니다")
                .isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("★ 복구는 직전 상태로만 간다")
    void restoreGoesToPreviousStatus() {
        Long article = at("복구할 공지", ArticleStatus.READY_TO_PUBLISH);
        adminArticleService.moveToTrash(List.of(article), null);

        adminArticleService.restore(List.of(article), "오분류");

        assertThat(statusOf(article)).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("★ 휴지통을 거치지 않은 공지는 복구할 수 없다")
    void cannotRestoreArticleThatWasNeverTrashed() {
        Long article = pending("휴지통에 간 적 없는 공지");

        BulkResult result = adminArticleService.restore(List.of(article), null);

        assertThat(result.succeeded()).isEmpty();
        assertThat(result.failed()).singleElement()
                .satisfies(failure -> assertThat(failure.code()).isEqualTo("NOT_IN_TRASH"));
    }

    @Test
    @DisplayName("여러 번 휴지통을 오간 공지는 가장 최근 상태로 복구된다")
    void restoreUsesMostRecentTrashLog() {
        Long article = pending("오락가락 공지");

        // 1회차: PENDING_REVIEW 에서 휴지통 → 복구
        adminArticleService.moveToTrash(List.of(article), null);
        adminArticleService.restore(List.of(article), null);

        // 2회차: READY_TO_PUBLISH 로 올린 뒤 휴지통
        adminArticleService.changeStatus(
                List.of(article), ArticleStatus.READY_TO_PUBLISH, null);
        adminArticleService.moveToTrash(List.of(article), null);

        adminArticleService.restore(List.of(article), null);

        assertThat(statusOf(article))
                .as("가장 오래된 이력을 집으면 엉뚱한 상태로 되돌아갑니다")
                .isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-12 확인 필요
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 정보가 온전한 공지는 '확인 필요' 에 잡히지 않는다")
    void completeArticleIsNotFlagged() {
        Long complete = completeArticle("모든 정보가 있는 공지");
        pending("기간도 카테고리도 없는 공지");

        List<AdminArticleSummary> flagged = needsCheck();

        assertThat(flagged).extracting(AdminArticleSummary::title)
                .containsExactly("기간도 카테고리도 없는 공지")
                .doesNotContain("모든 정보가 있는 공지");
    }

    @Test
    @DisplayName("중복 의심 점수가 임계값을 넘으면 잡힌다")
    void highSimilarityIsFlagged() {
        Long suspect = completeArticle("중복 의심 공지");
        Long other = completeArticle("비교 대상 공지");
        mutate(suspect, a -> a.markSimilarTo(new java.math.BigDecimal("85.00"), other));

        assertThat(needsCheck()).extracting(AdminArticleSummary::title).contains("중복 의심 공지");
        assertThat(needsCheck())
                .filteredOn(row -> "중복 의심 공지".equals(row.title()))
                .extracting(AdminArticleSummary::similarArticleId)
                .as("무엇과 비슷한지 없이는 병합 판단을 할 수 없습니다")
                .containsExactly(other);
    }

    @Test
    @DisplayName("본문이 사실상 비어 있으면 잡힌다 — 태그를 뺀 길이로 판정한다")
    void emptyContentIsFlagged() {
        Long article = completeArticle("껍데기 공지");
        // HTML 로는 길지만 태그를 빼면 몇 글자 안 됩니다.
        mutate(article, a -> a.edit("껍데기 공지", "<p><br></p><div><span></span></div>",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        assertThat(needsCheck()).extracting(AdminArticleSummary::title).contains("껍데기 공지");
    }

    @Test
    @DisplayName("★ 제목이 길어도 본문이 비었으면 '확인 필요' 에 잡힌다")
    void longTitleWithEmptyContentIsFlagged() {
        // search_text 는 제목 + 본문이라, 제목만으로 임계값을 넘기면 빈 본문이 가려집니다.
        String longTitle = "2026학년도 1학기 국가장학금 2차 신청 및 제출 서류 유의사항 안내 (인하대학교 학생지원팀)";
        Long article = completeArticle(longTitle);
        mutate(article, a -> a.edit(longTitle, "<p><br></p>",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        assertThat(needsCheck()).extracting(AdminArticleSummary::title)
                .as("제목이 아무리 길어도 본문이 비어 있으면 관리자가 봐야 합니다")
                .contains(longTitle);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 요청 정규화
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 같은 공지가 두 번 선택돼도 처리된다")
    void duplicateIdsAreTolerated() {
        Long article = pending("중복 선택될 공지");

        BulkResult result = adminArticleService.changeStatus(
                List.of(article, article), ArticleStatus.READY_TO_PUBLISH, null);

        assertThat(result.succeeded())
                .as("화면에서 같은 항목이 두 번 담기는 건 흔합니다")
                .containsExactly(article);
        assertThat(statusOf(article)).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("제목 검색어의 % 와 _ 는 와일드카드가 아니라 글자로 다뤄진다")
    void likeWildcardsAreEscaped() {
        pending("할인율 100% 적용 안내");
        pending("전혀 다른 공지");

        assertThat(search(byTitle("100%")))
                .as("% 를 그대로 넘기면 '100' 뒤에 무엇이 오든 걸립니다")
                .extracting(AdminArticleSummary::title)
                .containsExactly("할인율 100% 적용 안내");

        // % 를 글자로 다루므로, % 로 검색하면 제목에 % 가 든 공지만 나옵니다.
        // 이스케이프가 없다면 여기서 전체가 걸립니다.
        assertThat(search(byTitle("%")))
                .as("와일드카드로 해석되면 모든 공지가 걸립니다")
                .extracting(AdminArticleSummary::title)
                .containsExactly("할인율 100% 적용 안내");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-02 대시보드
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 대시보드 숫자가 각 목록 건수와 일치한다")
    void statsMatchListCounts() {
        pending("검수 대기 1");
        Long ready = pending("반영 대기가 될 것");
        completeArticle("확인 필요 없는 공지");
        adminArticleService.changeStatus(List.of(ready), ArticleStatus.READY_TO_PUBLISH, null);

        ReviewStats stats = adminArticleService.stats();

        assertThat(stats.pendingReview()).isEqualTo(count(ArticleStatus.PENDING_REVIEW));
        assertThat(stats.readyToPublish()).isEqualTo(count(ArticleStatus.READY_TO_PUBLISH));
        // ★ 유사도가 붙은 공지를 실제로 만들어야 0 == 0 비교가 아니게 됩니다.
        //   임계값 위/아래/휴지통 세 가지를 넣어 카드 조건을 전부 구분합니다.
        Long suspect = pending("중복 의심 공지");
        Long belowThreshold = pending("애매한 공지");
        Long trashedSuspect = at("휴지통에 간 의심 공지", ArticleStatus.TRASHED);
        mutate(suspect, a -> a.markSimilarTo(new java.math.BigDecimal("92.00"), belowThreshold));
        mutate(belowThreshold, a -> a.markSimilarTo(new java.math.BigDecimal("40.00"), suspect));
        mutate(trashedSuspect, a -> a.markSimilarTo(new java.math.BigDecimal("95.00"), suspect));

        assertThat(adminArticleService.stats().duplicateSuspected())
                .as("임계값 미만은 빼고, 휴지통도 뺍니다 — 목록의 기본 조건과 같아야 "
                        + "카드를 눌러 이동한 화면의 건수와 맞습니다")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 검색
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("제목 부분 일치와 제공처 필터")
    void searchFilters() {
        Long matching = pending("국가장학금 신청 안내");
        pending("전혀 다른 공지");
        link(matching, vendorId);

        assertThat(search(byTitle("장학"))).hasSize(1);
        assertThat(search(byVendor(vendorId)))
                .extracting(AdminArticleSummary::title).containsExactly("국가장학금 신청 안내");
    }

    @Test
    @DisplayName("★ 상태를 안 주면 휴지통만 빠진다 — 기본값 분기를 실제로 탄다")
    void omittingStatusReturnsEverythingButTrash() {
        Long pending = pending("검수 대기 공지");
        Long ready = at("발행 대기 공지", ArticleStatus.READY_TO_PUBLISH);
        Long published = at("배포된 공지",
                ArticleStatus.READY_TO_PUBLISH, ArticleStatus.PUBLISHED);
        Long trashed = at("휴지통 공지", ArticleStatus.TRASHED);

        // ★ statuses 를 null 로 줘야 buildWhere 의 else 분기를 탑니다.
        //   상태를 명시하면 IN (:statuses) 쪽만 검증되어, 기본값을 무엇으로 바꿔도 통과합니다.
        List<Long> found = search(new AdminArticleSearchCondition(
                null, null, null, null, null, null, null, null, null, null))
                .stream().map(AdminArticleSummary::id).toList();

        assertThat(found)
                .as("휴지통은 '지운 것' 이라 기본 목록에 섞이면 살아 있는 공지와 구분되지 않습니다")
                .contains(pending, ready, published)
                .doesNotContain(trashed);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AdminArticleSearchCondition byStatus(ArticleStatus status) {
        return new AdminArticleSearchCondition(
                List.of(status), null, null, null, null, null, null, null, null, null);
    }

    private AdminArticleSearchCondition byTitle(String title) {
        return new AdminArticleSearchCondition(
                null, null, null, title, null, null, null, null, null, null);
    }

    private AdminArticleSearchCondition byVendor(Long vendor) {
        return new AdminArticleSearchCondition(
                null, null, null, null, vendor, null, null, null, null, null);
    }

    private List<AdminArticleSummary> search(AdminArticleSearchCondition condition) {
        return adminArticleService.search(condition, PageRequest.of(0, 20)).getContent();
    }

    private List<AdminArticleSummary> needsCheck() {
        return search(new AdminArticleSearchCondition(
                List.of(ArticleStatus.PENDING_REVIEW), null, null, null, null, null,
                null, null, null, true));
    }

    /** 검수 대기 공지 하나. <b>커밋합니다</b> — 벌크의 새 트랜잭션이 볼 수 있어야 합니다. */
    private Long pending(String title) {
        Long id = tx.execute(status -> articleRepository
                .save(Article.createSchoolArticle(title, "내용", null, null, null)).getId());
        created.add(id);
        return id;
    }

    /** 상태를 직접 옮깁니다(준비용). 전이 규칙은 그대로 탑니다. */
    private Long at(String title, ArticleStatus... path) {
        Long id = pending(title);
        tx.executeWithoutResult(status -> {
            Article article = articleRepository.findById(id).orElseThrow();
            for (ArticleStatus next : path) {
                article.changeStatus(next);
            }
        });
        return id;
    }

    /** "확인 필요" 조건에 걸리지 않는 공지 — 기간·본문·카테고리·출처 URL 이 전부 있습니다. */
    /** "확인 필요" 조건에 걸리지 않는 공지 — 기간·본문·카테고리·출처 URL 이 전부 있습니다. */
    private Long completeArticle(String title) {
        Long id = tx.execute(status -> articleRepository.save(Article.createSchoolArticle(
                title, "본문이 충분히 길어야 합니다. ".repeat(5),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null)).getId());
        created.add(id);

        tx.executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO article_vendors (article_id, vendor_id, source_url) "
                            + "VALUES (:articleId, :vendorId, 'https://example.inha.ac.kr/1')")
                    .setParameter("articleId", id).setParameter("vendorId", vendorId).executeUpdate();
            em.createNativeQuery("INSERT INTO article_categories (article_id, category_id) "
                            + "VALUES (:articleId, :categoryId)")
                    .setParameter("articleId", id).setParameter("categoryId", categoryId).executeUpdate();
        });
        return id;
    }

    /** 엔티티를 한 트랜잭션 안에서 고칩니다. 헬퍼가 커밋하므로 밖에서는 손댈 수 없습니다. */
    private void mutate(Long articleId, java.util.function.Consumer<Article> change) {
        tx.executeWithoutResult(status ->
                change.accept(articleRepository.findById(articleId).orElseThrow()));
    }

    private void link(Long articleId, Long vendor) {
        tx.executeWithoutResult(status ->
                em.createNativeQuery("INSERT INTO article_vendors (article_id, vendor_id, source_url) "
                                + "VALUES (:articleId, :vendorId, 'https://example.inha.ac.kr/1')")
                        .setParameter("articleId", articleId).setParameter("vendorId", vendor)
                        .executeUpdate());
    }

    private ArticleStatus statusOf(Long articleId) {
        return ArticleStatus.valueOf(tx.execute(status ->
                (String) em.createNativeQuery("SELECT status FROM articles WHERE id = :id")
                        .setParameter("id", articleId).getSingleResult()));
    }

    private long count(ArticleStatus status) {
        return tx.execute(s -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM articles WHERE status = :status")
                .setParameter("status", status.name()).getSingleResult()).longValue());
    }

    private Long scalar(String sql) {
        return tx.execute(status ->
                ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }
}
