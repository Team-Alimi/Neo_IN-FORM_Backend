package today.inform.inform.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.article.service.ArticleReadableChecker;
import today.inform.inform.article.service.ArticleService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * V19 — 원문 게시일({@code source_published_at})과 공개 승인 증거({@code published_at})의 분리.
 *
 * <h2>이 파일이 막는 사고</h2>
 * {@code published_at} 은 날짜이면서 동시에 <b>"한 번이라도 공개된 적 있음"</b> 의 증거입니다.
 * {@code VISIBLE_OR_UNDER_REVIEW} 와 {@code ArticleReadableChecker} 가
 * {@code PENDING_REVIEW 이면서 published_at 이 있는} 것을 "배포됐다가 크롤러 수정으로
 * 재검수로 내려온 공지" 로 보고 접근을 허용하기 때문입니다 —
 * 사용자가 저장해 둔 공지가 404 가 되면 안 되니까요.
 *
 * <p>그 판정은 <b>"신규 수집분에는 published_at 이 없다"</b> 를 전제로만 성립합니다.
 * 크롤러가 학교 원문 게시일을 거기에 넣는 순간 전제가 깨지고,
 * <b>검수 전 공지 약 2,000건의 상세·북마크·좋아요·댓글이 한꺼번에 열립니다.</b>
 * 목록에는 안 뜨므로 아무도 모르는 채로 열려 있게 됩니다.
 *
 * <p>그래서 날짜를 {@code source_published_at} 으로 분리했고, 이 파일이
 * <b>그 분리가 유지되는지</b>를 잠급니다. 누군가 가시성 판정식에 {@code COALESCE} 를
 * 넣으면 여기서 깨집니다.
 *
 * <p>{@code source_published_at} 은 엔티티에서 {@code insertable=false} 라
 * 네이티브로 넣습니다 — 크롤러가 DB 에 직접 쓰는 경로를 그대로 흉내 냅니다.
 */
@Transactional
class SourcePublishedAtVisibilityTest extends IntegrationTest {

    /** 학교가 원문을 올린 날. 우리가 발행하기 한참 전입니다. */
    private static final OffsetDateTime SOURCE_DATE =
            OffsetDateTime.parse("2026-09-15T09:00:00+09:00");

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleReadableChecker readableChecker;

    @PersistenceContext
    private EntityManager em;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = insertUser("v19-reader@inha.ac.kr");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ★ 핵심 — 검수 전 공지는 원문 날짜가 있어도 닫혀 있어야 합니다
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★★ 미검수 공지는 원문 게시일이 있어도 상세가 열리지 않는다")
    void unreviewedStaysClosedEvenWithSourceDate() {
        Long articleId = collect("V19 검수 전 공지");
        setSourcePublishedAt(articleId, SOURCE_DATE);

        assertThatThrownBy(() -> articleService.getDetail(articleId, principal()))
                .as("원문 게시일이 공개 승인으로 오해되면 미검수 2,000건이 통째로 열립니다")
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★★ 미검수 공지는 원문 게시일이 있어도 북마크·좋아요·댓글이 거부된다")
    void unreviewedRejectsReactionsEvenWithSourceDate() {
        Long articleId = collect("V19 반응 차단");
        setSourcePublishedAt(articleId, SOURCE_DATE);

        // 북마크·좋아요·댓글이 모두 이 한 판정을 공유합니다.
        // 여기가 뚫리면 세 경로가 동시에 뚫립니다.
        assertThatThrownBy(() -> readableChecker.requireReadable(articleId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("미검수 공지는 원문 게시일이 있어도 목록에 나오지 않는다")
    void unreviewedStaysOutOfFeed() {
        Long articleId = collect("V19 목록 제외");
        setSourcePublishedAt(articleId, SOURCE_DATE);

        assertThat(feedTitles()).doesNotContain("V19 목록 제외");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 기존 공개글의 재검토 접근 — 현행 유지입니다
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 배포됐다가 재검수로 내려간 공지는 계속 열린다 — 저장해 둔 공지가 404 가 되면 안 됩니다")
    void demotedAfterPublishStaysReadable() {
        Article article = publish("V19 재검수 강등");
        Long articleId = article.getId();
        // 크롤러가 본문을 고치면 재검수로 내려갑니다. published_at 은 남습니다.
        article.changeStatus(ArticleStatus.PENDING_REVIEW);
        em.flush();
        em.clear();

        assertThat(articleService.getDetail(articleId, principal()).title())
                .isEqualTo("V19 재검수 강등");
        readableChecker.requireReadable(articleId);   // 던지지 않아야 합니다
    }

    @Test
    @DisplayName("재검수로 내려가도 목록에서는 빠진다 — 상세만 열립니다")
    void demotedDropsOutOfFeed() {
        Article article = publish("V19 강등 후 목록");
        article.changeStatus(ArticleStatus.PENDING_REVIEW);
        em.flush();
        em.clear();

        assertThat(feedTitles()).doesNotContain("V19 강등 후 목록");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 승인 후에는 원문 날짜를 보여 줍니다
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 발행된 공지는 우리 발행일이 아니라 학교 원문 게시일을 보여 준다")
    void publishedShowsSourceDate() {
        Article article = publish("V19 원문 날짜 표시");
        Long articleId = article.getId();
        setSourcePublishedAt(articleId, SOURCE_DATE);

        assertThat(articleService.getDetail(articleId, principal()).publishedAt())
                .as("9월에 올라온 공지가 '오늘 공지' 로 보이면 안 됩니다")
                .isEqualTo(SOURCE_DATE);
    }

    @Test
    @DisplayName("원문 게시일이 없으면 우리 발행일로 떨어진다 — 날짜가 비는 일은 없습니다")
    void fallsBackToOurPublishDate() {
        Article article = publish("V19 폴백");
        Long articleId = article.getId();
        em.clear();

        assertThat(articleService.getDetail(articleId, principal()).publishedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("목록에도 원문 게시일이 나간다 — 상세와 목록이 다른 날짜를 보이면 안 됩니다")
    void feedShowsSourceDateToo() {
        Article article = publish("V19 목록 날짜");
        setSourcePublishedAt(article.getId(), SOURCE_DATE);

        assertThat(feed().stream()
                .filter(summary -> "V19 목록 날짜".equals(summary.title()))
                .findFirst())
                .hasValueSatisfying(summary ->
                        assertThat(summary.publishedAt()).isEqualTo(SOURCE_DATE));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private AuthPrincipal principal() {
        return new AuthPrincipal(userId, UserRole.USER);
    }

    /** 크롤러가 막 수집해 넣은 상태. 검수 전이라 {@code published_at} 이 없습니다. */
    private Long collect(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "본문입니다", null, null, null));
        em.flush();
        return article.getId();
    }

    private Article publish(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "본문입니다", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    /**
     * 크롤러가 쓰는 경로를 흉내 냅니다.
     *
     * <p>엔티티는 이 컬럼을 {@code insertable=false, updatable=false} 로 두고 있어
     * 앱에서는 쓸 수 없습니다 — 의도된 제약입니다. 크롤러만 씁니다.
     */
    private void setSourcePublishedAt(Long articleId, OffsetDateTime at) {
        em.createNativeQuery("UPDATE articles SET source_published_at = :at WHERE id = :id")
                .setParameter("at", at)
                .setParameter("id", articleId)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private List<today.inform.inform.article.dto.response.ArticleSummaryResponse> feed() {
        // 필터 없는 빈 조건. null 을 넘기면 ArticleQueryValidator 가 먼저 터집니다.
        ArticleSearchCondition all =
                new ArticleSearchCondition(null, null, null, null, false, null, null, null);
        return articleService.search(all, PageRequest.of(0, 100), principal()).getContent();
    }

    private List<String> feedTitles() {
        return feed().stream()
                .map(today.inform.inform.article.dto.response.ArticleSummaryResponse::title)
                .toList();
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, 'V19', 'USER', 'ACTIVE')")
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }
}
