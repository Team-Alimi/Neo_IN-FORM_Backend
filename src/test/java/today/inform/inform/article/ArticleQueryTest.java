package today.inform.inform.article;

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
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.article.service.ArticleService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * 목록·상세 조회 SQL 을 실제 DB 위에서 확인합니다.
 *
 * <p>여기서 검증하는 건 대부분 <b>SQL 이 맞게 조립됐는가</b> 입니다.
 * 필터 조합·정렬·노출 기준은 문자열로 만들어지므로 컴파일러가 잡아 주지 않습니다.
 */
@Transactional
class ArticleQueryTest extends IntegrationTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleQueryRepository queryRepository;

    @Autowired
    private ArticleService articleService;

    @PersistenceContext
    private EntityManager em;

    private Long userId;
    private Long scholarshipCategoryId;
    private Long cseVendorId;

    @BeforeEach
    void setUp() {
        userId = insertUser("query@inha.ac.kr");
        scholarshipCategoryId = firstId("SELECT id FROM categories ORDER BY id");
        cseVendorId = insertVendor("질의테스트학과", "QRYTEST");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 노출 기준
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("목록에는 배포된 공지만 나온다")
    void listShowsPublishedOnly() {
        publish("배포된 공지");
        save("검수 대기 공지");   // PENDING_REVIEW, 한 번도 배포된 적 없음

        List<ArticleSummaryResponse> found = search(condition(null, null));

        assertThat(found).extracting(ArticleSummaryResponse::title).containsExactly("배포된 공지");
    }

    @Test
    @DisplayName("★ 재검수로 내려간 공지도 상세는 열린다 — 북마크에서 들어오면 404 가 나면 안 된다")
    void detailAllowsArticleUnderReview() {
        Article article = publish("배포 후 재검수된 공지");
        article.changeStatus(ArticleStatus.PENDING_REVIEW);
        em.flush();

        ArticleDetailResponse detail = queryRepository.findDetail(article.getId(), userId).orElseThrow();

        assertThat(detail.underReview())
                .as("프론트가 '검수 중' 배지를 띄우려면 이 값이 필요합니다")
                .isTrue();
    }

    @Test
    @DisplayName("한 번도 배포된 적 없는 공지는 상세도 열리지 않는다")
    void detailHidesNeverPublishedArticle() {
        Article article = save("신규 수집분");

        assertThat(queryRepository.findDetail(article.getId(), userId)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 필터
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 카테고리를 복수로 걸어도 같은 공지가 중복되지 않는다")
    void multipleCategoryFilterDoesNotDuplicate() {
        Article article = publish("카테고리 두 개짜리 공지");
        Long second = firstId("SELECT id FROM categories ORDER BY id OFFSET 1");
        link("article_categories", "category_id", article.getId(), scholarshipCategoryId);
        link("article_categories", "category_id", article.getId(), second);

        List<ArticleSummaryResponse> found = search(
                condition(List.of(scholarshipCategoryId, second), null));

        // JOIN 으로 짰다면 여기서 2건이 됩니다. total_elements 도 함께 틀어집니다.
        assertThat(found).hasSize(1);
    }

    @Test
    @DisplayName("제공처 필터")
    void vendorFilter() {
        Article matching = publish("우리 학과 공지");
        publish("다른 공지");
        link("article_vendors", "vendor_id", matching.getId(), cseVendorId);

        List<ArticleSummaryResponse> found = search(condition(null, List.of(cseVendorId)));

        assertThat(found).extracting(ArticleSummaryResponse::title).containsExactly("우리 학과 공지");
    }

    @Test
    @DisplayName("★ 두 글자 한글 검색이 된다 — pg_bigm 을 쓰는 이유")
    void twoLetterKoreanSearch() {
        publish("2026학년도 국가장학금 신청 안내");
        publish("교내 취업 특강 안내");

        List<ArticleSummaryResponse> found = search(new ArticleSearchCondition(
                null, null, null, "장학", false, null, null, null));

        assertThat(found).extracting(ArticleSummaryResponse::title)
                .containsExactly("2026학년도 국가장학금 신청 안내");
    }

    @Test
    @DisplayName("검색은 본문도 훑고 태그는 무시한다")
    void searchCoversContentWithoutTags() {
        Article article = publish("제목에는 없는 단어");
        article.edit("제목에는 없는 단어", "<p>본문에 <b>인턴</b> 모집 안내</p>", null, null);
        em.flush();

        assertThat(search(new ArticleSearchCondition(
                null, null, null, "인턴", false, null, null, null)))
                .hasSize(1);
    }

    @Test
    @DisplayName("관심 분야만 보기는 기본으로 켜져 있다")
    void interestOnlyIsOnByDefault() {
        Article mine = publish("내 관심 분야 공지");
        publish("관심 없는 공지");
        link("article_categories", "category_id", mine.getId(), scholarshipCategoryId);
        link("user_interest_categories", "category_id", userId, scholarshipCategoryId, "user_id");

        // interestOnly 를 주지 않았습니다.
        List<ArticleSummaryResponse> found = search(new ArticleSearchCondition(
                null, null, null, null, null, null, null, null));

        assertThat(found).extracting(ArticleSummaryResponse::title).containsExactly("내 관심 분야 공지");
    }

    @Test
    @DisplayName("기간 필터는 날짜 없는 공지를 제외한다")
    void periodFilterExcludesArticlesWithoutDates() {
        Article event = publish("3월 행사");
        event.edit("3월 행사", "내용", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        publish("날짜 없는 일반 안내");
        em.flush();

        List<ArticleSummaryResponse> found = search(new ArticleSearchCondition(
                null, null, null, null, false,
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20), null));

        assertThat(found).extracting(ArticleSummaryResponse::title).containsExactly("3월 행사");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 개인화 · 부가 정보
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("is_bookmarked / is_liked 가 본인 기준으로 채워진다")
    void personalizationFlags() {
        Article bookmarked = publish("북마크한 공지");
        publish("안 한 공지");
        link("bookmarks", "article_id", userId, bookmarked.getId(), "user_id");

        List<ArticleSummaryResponse> found = search(condition(null, null));

        assertThat(found).filteredOn(ArticleSummaryResponse::isBookmarked)
                .extracting(ArticleSummaryResponse::title)
                .containsExactly("북마크한 공지");
        assertThat(found).noneMatch(ArticleSummaryResponse::isLiked);
    }

    @Test
    @DisplayName("목록의 제공처·카테고리 이름이 채워진다")
    void listCarriesNames() {
        Article article = publish("이름이 붙는 공지");
        link("article_vendors", "vendor_id", article.getId(), cseVendorId);
        link("article_categories", "category_id", article.getId(), scholarshipCategoryId);

        ArticleSummaryResponse found = search(condition(null, null)).get(0);

        assertThat(found.vendors()).extracting(ArticleSummaryResponse.NamedRef::name)
                .containsExactly("질의테스트학과");
        assertThat(found.categories()).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 정렬 · 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("허용되지 않은 정렬 기준은 400 이다")
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> queryRepository.search(
                condition(null, null), userId,
                PageRequest.of(0, 20, Sort.by("view_count"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 한 글자 검색은 거부한다 — 인덱스가 후보를 못 좁혀 전수 확인이 된다")
    void rejectsSingleCharacterKeyword() {
        assertThatThrownBy(() -> articleService.search(
                new ArticleSearchCondition(null, null, null, "학", false, null, null, null),
                PageRequest.of(0, 20), principal(UserRole.USER)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 마감 임박순은 마감 필터와 함께만 허용한다")
    void deadlineSortRequiresDeadlineFilter() {
        assertThatThrownBy(() -> articleService.search(
                condition(null, null),
                PageRequest.of(0, 20, Sort.by("ends_on")), principal(UserRole.USER)))
                .isInstanceOf(BusinessException.class);

        // 함께 오면 통과합니다.
        articleService.search(
                new ArticleSearchCondition(null, null, null, null, false, null, null, true),
                PageRequest.of(0, 20, Sort.by("ends_on")), principal(UserRole.USER));
    }

    @Test
    @DisplayName("없는 공지 상세는 404 다")
    void detailNotFound() {
        assertThatThrownBy(() -> articleService.getDetail(999_999L, principal(UserRole.USER)))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<ArticleSummaryResponse> search(ArticleSearchCondition condition) {
        return queryRepository.search(condition, userId, PageRequest.of(0, 20)).getContent();
    }

    /** interestOnly 를 끈 기본 조건. 대부분의 테스트는 관심 분야를 설정하지 않습니다. */
    private ArticleSearchCondition condition(List<Long> categoryIds, List<Long> vendorIds) {
        return new ArticleSearchCondition(null, categoryIds, vendorIds, null, false, null, null, null);
    }

    private AuthPrincipal principal(UserRole role) {
        return new AuthPrincipal(userId, role);
    }

    private Article save(String title) {
        return articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, null, null));
    }

    private Article publish(String title) {
        Article article = save(title);
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '질의테스터', 'USER', 'ACTIVE')")
                .setParameter("email", email).executeUpdate();
        return firstId("SELECT id FROM users WHERE email = '" + email + "'");
    }

    private Long insertVendor(String name, String initial) {
        em.createNativeQuery("INSERT INTO vendors (name, initial, type) "
                        + "VALUES (:name, :initial, 'SCHOOL')")
                .setParameter("name", name).setParameter("initial", initial).executeUpdate();
        return firstId("SELECT id FROM vendors WHERE initial = '" + initial + "'");
    }

    private void link(String table, String column, Long left, Long right) {
        link(table, column, left, right, "article_id");
    }

    private void link(String table, String column, Long left, Long right, String leftColumn) {
        em.createNativeQuery("INSERT INTO " + table + " (" + leftColumn + ", " + column + ") "
                        + "VALUES (:left, :right)")
                .setParameter("left", left).setParameter("right", right).executeUpdate();
    }

    private Long firstId(String sql) {
        return ((Number) em.createNativeQuery(sql + " LIMIT 1").getSingleResult()).longValue();
    }

    /** SourceType 을 쓰는 테스트가 생기면 지웁니다. 지금은 import 유지용이 아니라 실제 사용처입니다. */
    @Test
    @DisplayName("출처 유형 필터")
    void sourceTypeFilter() {
        publish("학교 공지");
        Article club = articleRepository.saveAndFlush(
                Article.createClubArticle("동아리 공지", "내용", null, null, null));
        club.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();

        List<ArticleSummaryResponse> found = queryRepository.search(
                new ArticleSearchCondition(SourceType.CLUB, null, null, null, false, null, null, null),
                userId, PageRequest.of(0, 20)).getContent();

        assertThat(found).extracting(ArticleSummaryResponse::title).containsExactly("동아리 공지");
    }
}
