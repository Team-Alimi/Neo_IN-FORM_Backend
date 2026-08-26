package today.inform.inform.calendar;

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
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.calendar.dto.request.CalendarQuery;
import today.inform.inform.calendar.service.CalendarService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * CAL-01 월간 일정 · CAL-02 내 학과 필터.
 *
 * <p><b>겹침 판정이 이 기능의 전부입니다.</b> 한쪽 날짜만 있는 공지를 빠뜨리면
 * "상시 모집, 12월 마감" 같은 공지가 달력에서 통째로 사라지는데,
 * 오류가 아니라 <b>그 달에 원래 없는 것처럼</b> 보입니다.
 */
@Transactional
class CalendarTest extends IntegrationTest {

    /** 기준 달: 2026년 5월. */
    private static final int YEAR = 2026;
    private static final int MONTH = 5;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    private Long userId;
    private Long categoryId;
    private Long otherCategoryId;

    @BeforeEach
    void setUp() {
        userId = insertUser("calendar@inha.ac.kr");
        categoryId = firstId("SELECT id FROM categories ORDER BY id");
        otherCategoryId = firstId("SELECT id FROM categories ORDER BY id OFFSET 1");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 겹침 판정
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("그 달에 걸치기만 하면 나온다 — 시작·끝이 달 밖이어도")
    void spanningArticleIsIncluded() {
        Long spanning = publish("4월에 시작해 6월에 끝나는 공지",
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 6, 10));
        Long inside = publish("5월 안에 다 있는 공지",
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20));
        Long before = publish("4월에 끝난 공지",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        Long after = publish("6월에 시작하는 공지",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        em.flush();

        assertThat(ids(monthly()))
                .contains(spanning, inside)
                .doesNotContain(before, after);
    }

    @Test
    @DisplayName("★ 한쪽 날짜만 있는 공지도 나온다 — 없는 쪽은 무한으로 본다")
    void openEndedArticlesAreIncluded() {
        Long noStart = publish("상시 모집, 5월 말 마감", null, LocalDate.of(2026, 5, 31));
        Long noEnd = publish("5월 시작, 마감 없음", LocalDate.of(2026, 5, 1), null);
        Long endedEarlier = publish("상시 모집, 4월 마감", null, LocalDate.of(2026, 4, 30));
        em.flush();

        assertThat(ids(monthly()))
                .as("빠뜨리면 '상시 모집' 공지가 달력에서 통째로 사라집니다")
                .contains(noStart, noEnd)
                .doesNotContain(endedEarlier);
    }

    @Test
    @DisplayName("★ 날짜가 하나도 없는 공지는 나오지 않는다 — 달력에 놓을 자리가 없다")
    void articleWithoutAnyDateIsExcluded() {
        Long dateless = publish("날짜 없는 일반 안내", null, null);
        em.flush();

        assertThat(ids(monthly())).doesNotContain(dateless);
    }

    @Test
    @DisplayName("경계일이 딱 걸쳐도 나온다")
    void boundaryDatesAreInclusive() {
        Long firstDay = publish("5월 1일 하루", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
        Long lastDay = publish("5월 31일 하루", LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 31));
        em.flush();

        assertThat(ids(monthly())).contains(firstDay, lastDay);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 노출 기준
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 배포되지 않은 공지는 게스트 달력에 새어 나가지 않는다")
    void unpublishedArticlesNeverLeak() {
        Article neverPublished = articleRepository.saveAndFlush(Article.createSchoolArticle(
                "한 번도 배포된 적 없는 공지", "내용",
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20), null));

        // ★ 재검수로 내려간 공지. published_at 이 채워져 있어 상세·북마크 기준
        //   (VISIBLE_OR_UNDER_REVIEW)으로는 보이는 상태입니다.
        //   이 대조군이 없으면 두 노출 기준이 같은 답을 내서, 캘린더가 넓은 기준으로
        //   바뀌어도 테스트가 반응하지 않습니다.
        Long underReview = publish("배포됐다가 재검수로 내려간 공지",
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20));
        articleRepository.findById(underReview).orElseThrow()
                .changeStatus(ArticleStatus.PENDING_REVIEW);
        em.flush();

        assertThat(publishedAt(underReview))
                .as("이 대조군이 성립하려면 published_at 이 남아 있어야 합니다")
                .isNotNull();

        assertThat(ids(monthly()))
                .as("달력은 비로그인도 열립니다 — 여기서 새면 아무나 미배포 공지를 봅니다")
                .doesNotContain(neverPublished.getId(), underReview);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 필터
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("분류로 거른다")
    void filtersByCategory() {
        Long wanted = publish("장학 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));
        Long other = publish("다른 분류 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));
        link(wanted, categoryId);
        link(other, otherCategoryId);
        em.flush();

        List<ArticleSummaryResponse> found = calendarService.findMonthly(
                new CalendarQuery(YEAR, MONTH, List.of(categoryId), false, false), guest());

        assertThat(ids(found)).contains(wanted).doesNotContain(other);
    }

    @Test
    @DisplayName("★ CAL-02 구독한 학과의 공지만 거른다 — 구독하지 않은 학과 공지가 대조군이다")
    void filtersByMyMajor() {
        Long subscribed = insertVendor("구독한학과", "CALSUB");
        Long notSubscribed = insertVendor("구독안한학과", "CALNOSUB");

        Long mine = publish("내 학과 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));
        Long otherVendor = publish("남의 학과 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));
        Long noVendor = publish("제공처 없는 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));

        attachVendor(mine, subscribed);
        // ★ 이 줄이 핵심입니다. 대조군에 제공처를 붙이지 않으면 조건이
        //   "구독한 학과인가" 에서 "제공처가 붙어 있는가" 로 약해져도 결과가 같아 통과합니다.
        attachVendor(otherVendor, notSubscribed);
        subscribe(userId, subscribed);
        em.flush();

        List<ArticleSummaryResponse> found = calendarService.findMonthly(
                new CalendarQuery(YEAR, MONTH, null, true, false), user());

        assertThat(ids(found))
                .contains(mine)
                .doesNotContain(otherVendor, noVendor);
    }

    @Test
    @DisplayName("★ 관심 카테고리만 보기가 캘린더에서도 걸린다")
    void interestOnlyFiltersCalendar() {
        Long mine = publish("관심 있는 일정", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 10));
        Long other = publish("관심 없는 일정", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 11));
        link(mine, categoryId);
        // ★ 대조군에도 카테고리를 붙입니다. 안 붙이면 조건이 "내 관심 카테고리인가" 에서
        //   "카테고리가 붙어 있는가" 로 약해져도 결과가 같아 통과합니다.
        link(other, otherCategoryId);
        addInterest(userId, categoryId);
        em.flush();

        List<ArticleSummaryResponse> found = calendarService.findMonthly(
                new CalendarQuery(YEAR, MONTH, null, false, true), user());

        assertThat(ids(found)).contains(mine).doesNotContain(other);
    }

    @Test
    @DisplayName("★ 관심 카테고리가 없는 사용자는 빈 달력을 받는다 — 폴백이 없다")
    void interestOnlyWithNoInterestsIsEmpty() {
        Long article = publish("아무 일정", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 10));
        link(article, categoryId);
        em.flush();

        List<ArticleSummaryResponse> found = calendarService.findMonthly(
                new CalendarQuery(YEAR, MONTH, null, false, true), user());

        assertThat(found)
                .as("공지 목록의 interest_only 와 같은 동작입니다. 그래서 기본이 꺼짐입니다")
                .isEmpty();
    }

    @Test
    @DisplayName("비로그인으로 '관심 카테고리만' 을 요청하면 401")
    void interestOnlyRequiresLogin() {
        assertThatThrownBy(() ->
                calendarService.findMonthly(new CalendarQuery(YEAR, MONTH, null, false, true), guest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 같은 게시판에 재게시된 공지의 제공처 칩이 중복으로 그려지지 않는다")
    void repostedArticleShowsVendorOnce() {
        Long vendorId = insertVendor("재게시학과", "CALREPOST");
        Long articleId = publish("재게시된 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));

        // article_vendors 는 (article_id, vendor_id) 유니크를 의도적으로 안 겁니다 —
        // 같은 게시판 재게시를 모두 보존해야 재수집 루프가 안 생기기 때문입니다(V2 주석).
        // 즉 아래 두 행은 정상 상태이고, DISTINCT 가 없으면 칩이 두 개로 그려집니다.
        crawledVendor(articleId, vendorId, "1001");
        crawledVendor(articleId, vendorId, "1002");
        em.flush();

        assertThat(monthly())
                .filteredOn(row -> row.id().equals(articleId))
                .singleElement()
                .satisfies(row -> assertThat(row.vendors())
                        .as("오류 없이 200 으로 나가므로 화면을 보기 전에는 드러나지 않습니다")
                        .hasSize(1));
    }

    @Test
    @DisplayName("★ 비로그인으로 '내 학과만' 을 요청하면 401 — 조용히 빈 목록을 주면 안 된다")
    void myMajorOnlyRequiresLogin() {
        assertThatThrownBy(() ->
                calendarService.findMonthly(new CalendarQuery(YEAR, MONTH, null, true, false), guest()))
                .as("빈 목록은 '내 학과 공지가 없다' 와 구분되지 않습니다")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 입력 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 잘못된 월은 400 — LocalDate 가 던지는 예외는 SQLSTATE 가 없어 500 이 된다")
    void invalidMonthIsRejected() {
        assertThatThrownBy(() -> new CalendarQuery(YEAR, 13, null, false, false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new CalendarQuery(YEAR, 0, null, false, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("터무니없는 연도는 400")
    void invalidYearIsRejected() {
        assertThatThrownBy(() -> new CalendarQuery(999_999_999, MONTH, null, false, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 게스트에게는 남의 북마크가 보이지 않고, 본인에게는 자기 북마크가 보인다")
    void personalisationIsScopedToTheViewer() {
        Long articleId = publish("개인화 확인 공지", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 15));

        // ★ 실제로 북마크·좋아요 행이 있어야 검증이 성립합니다.
        //   행이 하나도 없으면 어떤 userId 를 넣어도 false 라, "비로그인이라서 false" 와
        //   "데이터가 없어서 false" 가 구분되지 않습니다.
        react("bookmarks", userId, articleId);
        react("article_likes", userId, articleId);
        em.flush();

        assertThat(monthly())
                .filteredOn(row -> row.id().equals(articleId))
                .as("게스트 자리에 실재하는 사용자 번호가 들어가면 남의 하트가 켜진 채로 그려집니다")
                .allSatisfy(row -> {
                    assertThat(row.isBookmarked()).isFalse();
                    assertThat(row.isLiked()).isFalse();
                });

        List<ArticleSummaryResponse> mine = calendarService.findMonthly(
                new CalendarQuery(YEAR, MONTH, null, false, false), user());

        assertThat(mine)
                .filteredOn(row -> row.id().equals(articleId))
                .as("반대로 로그인 사용자에게는 자기 북마크가 보여야 합니다 — 안 그러면 달력에서만 해제된 것처럼 보입니다")
                .allSatisfy(row -> {
                    assertThat(row.isBookmarked()).isTrue();
                    assertThat(row.isLiked()).isTrue();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<ArticleSummaryResponse> monthly() {
        return calendarService.findMonthly(new CalendarQuery(YEAR, MONTH, null, false, false), guest());
    }

    private static List<Long> ids(List<ArticleSummaryResponse> rows) {
        return rows.stream().map(ArticleSummaryResponse::id).toList();
    }

    /** 비로그인 요청. {@code @AuthenticationPrincipal} 이 null 로 들어오는 상황입니다. */
    private static AuthPrincipal guest() {
        return null;
    }

    private void addInterest(Long user, Long category) {
        em.createNativeQuery("INSERT INTO user_interest_categories (user_id, category_id) "
                        + "VALUES (:user, :category)")
                .setParameter("user", user).setParameter("category", category).executeUpdate();
    }

    private AuthPrincipal user() {
        return new AuthPrincipal(userId, UserRole.USER);
    }

    private Long publish(String title, LocalDate startsOn, LocalDate endsOn) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", startsOn, endsOn, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article.getId();
    }

    private void crawledVendor(Long articleId, Long vendorId, String externalKey) {
        em.createNativeQuery("""
                        INSERT INTO article_vendors (article_id, vendor_id, source_url, external_key)
                        VALUES (:a, :v, 'https://inha.ac.kr/board/' || :k, :k)
                        """)
                .setParameter("a", articleId).setParameter("v", vendorId)
                .setParameter("k", externalKey).executeUpdate();
    }

    private void react(String table, Long user, Long articleId) {
        em.createNativeQuery("INSERT INTO " + table + " (user_id, article_id) VALUES (:u, :a)")
                .setParameter("u", user).setParameter("a", articleId).executeUpdate();
    }

    private Object publishedAt(Long articleId) {
        return em.createNativeQuery("SELECT published_at FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult();
    }

    private void link(Long articleId, Long category) {
        em.createNativeQuery("INSERT INTO article_categories (article_id, category_id) VALUES (:a, :c)")
                .setParameter("a", articleId).setParameter("c", category).executeUpdate();
    }

    private void attachVendor(Long articleId, Long vendorId) {
        em.createNativeQuery("INSERT INTO article_vendors (article_id, vendor_id) VALUES (:a, :v)")
                .setParameter("a", articleId).setParameter("v", vendorId).executeUpdate();
    }

    private void subscribe(Long user, Long vendorId) {
        em.createNativeQuery("INSERT INTO user_vendors (user_id, vendor_id) VALUES (:u, :v)")
                .setParameter("u", user).setParameter("v", vendorId).executeUpdate();
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '캘린더테스터', 'USER', 'ACTIVE')")
                .setParameter("email", email).executeUpdate();
        return firstId("SELECT id FROM users WHERE email = '" + email + "'");
    }

    private Long insertVendor(String name, String initial) {
        em.createNativeQuery("INSERT INTO vendors (name, initial, type) VALUES (:n, :i, 'SCHOOL')")
                .setParameter("n", name).setParameter("i", initial).executeUpdate();
        return firstId("SELECT id FROM vendors WHERE initial = '" + initial + "'");
    }

    private Long firstId(String sql) {
        return ((Number) em.createNativeQuery(sql + " LIMIT 1").getSingleResult()).longValue();
    }
}
