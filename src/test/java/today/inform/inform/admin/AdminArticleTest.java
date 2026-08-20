package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
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
 */
@Transactional
class AdminArticleTest extends IntegrationTest {

    @Autowired
    private AdminArticleService adminArticleService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    private Long vendorId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        vendorId = insertVendor("관리자테스트학과", "ADMTEST");
        categoryId = firstId("SELECT id FROM categories ORDER BY id");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-07 상태 전이
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 검수를 건너뛴 일괄 발행은 전부 실패한다 — 부분 성공을 남기지 않는다")
    void skippingReviewFailsEntireBatch() {
        Article ok = pending("정상 검수 대기");
        Article skipping = pending("건너뛰려는 공지");
        ok.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        em.flush();

        assertThatThrownBy(() -> adminArticleService.changeStatus(
                List.of(ok.getId(), skipping.getId()), ArticleStatus.PUBLISHED, "일괄 발행"))
                .isInstanceOf(BusinessException.class);

        em.clear();
        assertThat(statusOf(ok.getId()))
                .as("한 건이 막히면 나머지도 되돌아가야 합니다")
                .isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("정상 경로는 일괄로 처리된다")
    void bulkTransitionSucceeds() {
        Article first = pending("공지 1");
        Article second = pending("공지 2");
        em.flush();

        int changed = adminArticleService.changeStatus(
                List.of(first.getId(), second.getId()), ArticleStatus.READY_TO_PUBLISH, "일괄 검수");
        em.flush();

        assertThat(changed).isEqualTo(2);
        assertThat(statusOf(first.getId())).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("★ 없는 공지 번호가 섞이면 전부 실패한다 — 조용히 건너뛰지 않는다")
    void missingArticleFailsBatch() {
        Article article = pending("있는 공지");
        em.flush();

        assertThatThrownBy(() -> adminArticleService.changeStatus(
                List.of(article.getId(), 999_999L), ArticleStatus.READY_TO_PUBLISH, null))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 감사 이력
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 상태를 바꾸면 사유가 이력에 남는다")
    void memoIsRecorded() {
        Article article = pending("사유 기록 대상");
        em.flush();

        adminArticleService.changeStatus(
                List.of(article.getId()), ArticleStatus.READY_TO_PUBLISH, "오탈자 확인 완료");
        em.flush();

        List<StatusLogResponse> logs = adminArticleService.statusLogs(article.getId());

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
        Article article = pending("사유 없는 변경");
        em.flush();

        adminArticleService.changeStatus(List.of(article.getId()), ArticleStatus.READY_TO_PUBLISH, null);
        em.flush();

        assertThat(adminArticleService.statusLogs(article.getId()).get(0).memo()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-08 / ADM-09 휴지통
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 휴지통 목록은 들어가기 직전 상태를 보여준다")
    void trashListShowsPreviousStatus() {
        Article article = pending("휴지통 갈 공지");
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        em.flush();

        adminArticleService.moveToTrash(List.of(article.getId()), "중복");
        em.flush();
        em.clear();

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
        Article article = pending("복구할 공지");
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        em.flush();
        adminArticleService.moveToTrash(List.of(article.getId()), null);
        em.flush();
        em.clear();

        adminArticleService.restore(List.of(article.getId()), "오분류");
        em.flush();
        em.clear();

        assertThat(statusOf(article.getId())).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("★ 휴지통을 거치지 않은 공지는 복구할 수 없다")
    void cannotRestoreArticleThatWasNeverTrashed() {
        Article article = pending("휴지통에 간 적 없는 공지");
        em.flush();

        assertThatThrownBy(() -> adminArticleService.restore(List.of(article.getId()), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("여러 번 휴지통을 오간 공지는 가장 최근 상태로 복구된다")
    void restoreUsesMostRecentTrashLog() {
        Article article = pending("오락가락 공지");
        em.flush();

        // 1회차: PENDING_REVIEW 에서 휴지통 → 복구
        adminArticleService.moveToTrash(List.of(article.getId()), null);
        em.flush();
        adminArticleService.restore(List.of(article.getId()), null);
        em.flush();

        // 2회차: READY_TO_PUBLISH 로 올린 뒤 휴지통
        adminArticleService.changeStatus(
                List.of(article.getId()), ArticleStatus.READY_TO_PUBLISH, null);
        em.flush();
        adminArticleService.moveToTrash(List.of(article.getId()), null);
        em.flush();
        em.clear();

        adminArticleService.restore(List.of(article.getId()), null);
        em.flush();
        em.clear();

        assertThat(statusOf(article.getId()))
                .as("가장 오래된 이력을 집으면 엉뚱한 상태로 되돌아갑니다")
                .isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-12 확인 필요
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 정보가 온전한 공지는 '확인 필요' 에 잡히지 않는다")
    void completeArticleIsNotFlagged() {
        Article complete = completeArticle("모든 정보가 있는 공지");
        pending("기간도 카테고리도 없는 공지");
        em.flush();

        List<AdminArticleSummary> flagged = needsCheck();

        assertThat(flagged).extracting(AdminArticleSummary::title)
                .containsExactly("기간도 카테고리도 없는 공지")
                .doesNotContain(complete.getTitle());
    }

    @Test
    @DisplayName("중복 의심 점수가 임계값을 넘으면 잡힌다")
    void highSimilarityIsFlagged() {
        Article suspect = completeArticle("중복 의심 공지");
        Article other = completeArticle("비교 대상 공지");
        em.flush();
        suspect.markSimilarTo(new java.math.BigDecimal("85.00"), other.getId());
        em.flush();

        assertThat(needsCheck()).extracting(AdminArticleSummary::title).contains("중복 의심 공지");
        assertThat(needsCheck())
                .filteredOn(row -> "중복 의심 공지".equals(row.title()))
                .extracting(AdminArticleSummary::similarArticleId)
                .as("무엇과 비슷한지 없이는 병합 판단을 할 수 없습니다")
                .containsExactly(other.getId());
    }

    @Test
    @DisplayName("본문이 사실상 비어 있으면 잡힌다 — 태그를 뺀 길이로 판정한다")
    void emptyContentIsFlagged() {
        Article article = completeArticle("껍데기 공지");
        em.flush();
        // HTML 로는 길지만 태그를 빼면 몇 글자 안 됩니다.
        article.edit("껍데기 공지", "<p><br></p><div><span></span></div>",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        em.flush();

        assertThat(needsCheck()).extracting(AdminArticleSummary::title).contains("껍데기 공지");
    }

    @Test
    @DisplayName("★ 제목이 길어도 본문이 비었으면 '확인 필요' 에 잡힌다")
    void longTitleWithEmptyContentIsFlagged() {
        // search_text 는 제목 + 본문이라, 제목만으로 임계값을 넘기면 빈 본문이 가려집니다.
        String longTitle = "2026학년도 1학기 국가장학금 2차 신청 및 제출 서류 유의사항 안내 (인하대학교 학생지원팀)";
        Article article = completeArticle(longTitle);
        em.flush();
        article.edit(longTitle, "<p><br></p>", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        em.flush();

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
        Article article = pending("중복 선택될 공지");
        em.flush();

        int changed = adminArticleService.changeStatus(
                List.of(article.getId(), article.getId()), ArticleStatus.READY_TO_PUBLISH, null);
        em.flush();

        assertThat(changed).as("화면에서 같은 항목이 두 번 담기는 건 흔합니다").isEqualTo(1);
        assertThat(statusOf(article.getId())).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("제목 검색어의 % 와 _ 는 와일드카드가 아니라 글자로 다뤄진다")
    void likeWildcardsAreEscaped() {
        pending("할인율 100% 적용 안내");
        pending("전혀 다른 공지");
        em.flush();

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
        Article ready = pending("반영 대기가 될 것");
        completeArticle("확인 필요 없는 공지");
        em.flush();
        adminArticleService.changeStatus(List.of(ready.getId()), ArticleStatus.READY_TO_PUBLISH, null);
        em.flush();

        ReviewStats stats = adminArticleService.stats();

        assertThat(stats.pendingReview()).isEqualTo(count(ArticleStatus.PENDING_REVIEW));
        assertThat(stats.readyToPublish()).isEqualTo(count(ArticleStatus.READY_TO_PUBLISH));
        assertThat(stats.needsCheck())
                .as("카드 숫자와 눌러서 가는 목록이 다르면 관리자는 무엇을 믿어야 할지 모릅니다")
                .isEqualTo(needsCheck().size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 검색
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("제목 부분 일치와 제공처 필터")
    void searchFilters() {
        Article matching = pending("국가장학금 신청 안내");
        pending("전혀 다른 공지");
        em.flush();
        link(matching.getId(), vendorId);

        assertThat(search(byTitle("장학"))).hasSize(1);
        assertThat(search(byVendor(vendorId)))
                .extracting(AdminArticleSummary::title).containsExactly("국가장학금 신청 안내");
    }

    @Test
    @DisplayName("상태를 지정하지 않으면 검수 대기가 기본이다")
    void statusDefaultsToPendingReview() {
        pending("검수 대기 공지");
        Article published = pending("배포될 공지");
        em.flush();
        published.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        published.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();

        assertThat(search(new AdminArticleSearchCondition(null, null, null, null, null, null, null, null)))
                .extracting(AdminArticleSummary::title)
                .containsExactly("검수 대기 공지");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AdminArticleSearchCondition byTitle(String title) {
        return new AdminArticleSearchCondition(null, null, title, null, null, null, null, null);
    }

    private AdminArticleSearchCondition byVendor(Long vendor) {
        return new AdminArticleSearchCondition(null, null, null, vendor, null, null, null, null);
    }

    private List<AdminArticleSummary> search(AdminArticleSearchCondition condition) {
        return adminArticleService.search(condition, PageRequest.of(0, 20)).getContent();
    }

    private List<AdminArticleSummary> needsCheck() {
        return search(new AdminArticleSearchCondition(
                ArticleStatus.PENDING_REVIEW, null, null, null, null, null, null, true));
    }

    private Article pending(String title) {
        return articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, null, null));
    }

    /** "확인 필요" 조건에 걸리지 않는 공지 — 기간·본문·카테고리·출처 URL 이 전부 있습니다. */
    private Article completeArticle(String title) {
        Article article = articleRepository.saveAndFlush(Article.createSchoolArticle(
                title, "본문이 충분히 길어야 합니다. ".repeat(5),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null));
        em.flush();
        link(article.getId(), vendorId);
        em.createNativeQuery("INSERT INTO article_categories (article_id, category_id) "
                        + "VALUES (:articleId, :categoryId)")
                .setParameter("articleId", article.getId()).setParameter("categoryId", categoryId)
                .executeUpdate();
        return article;
    }

    private void link(Long articleId, Long vendor) {
        em.createNativeQuery("INSERT INTO article_vendors (article_id, vendor_id, source_url) "
                        + "VALUES (:articleId, :vendorId, 'https://example.inha.ac.kr/1')")
                .setParameter("articleId", articleId).setParameter("vendorId", vendor)
                .executeUpdate();
    }

    private ArticleStatus statusOf(Long articleId) {
        return ArticleStatus.valueOf((String) em.createNativeQuery(
                        "SELECT status FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult());
    }

    private long count(ArticleStatus status) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM articles WHERE status = :status")
                .setParameter("status", status.name()).getSingleResult()).longValue();
    }

    private Long insertVendor(String name, String initial) {
        em.createNativeQuery("INSERT INTO vendors (name, initial, type) VALUES (:name, :initial, 'SCHOOL')")
                .setParameter("name", name).setParameter("initial", initial).executeUpdate();
        return firstId("SELECT id FROM vendors WHERE initial = '" + initial + "'");
    }

    private Long firstId(String sql) {
        return ((Number) em.createNativeQuery(sql + " LIMIT 1").getSingleResult()).longValue();
    }
}
