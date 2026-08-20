package today.inform.inform.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.bookmark.service.BookmarkService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.like.service.LikeService;
import today.inform.inform.support.IntegrationTest;

/**
 * BMK-01~04, LIK-01.
 *
 * <p>두 기능이 구조가 같아서 한 클래스에서 봅니다.
 * 검증 대상은 대부분 <b>멱등성</b>과 <b>카운터가 트리거와 어긋나지 않는가</b> 입니다.
 */
@Transactional
class BookmarkAndLikeTest extends IntegrationTest {

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleQueryRepository articleQueryRepository;

    @PersistenceContext
    private EntityManager em;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = insertUser("bookmark@inha.ac.kr");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 멱등성
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 북마크를 두 번 눌러도 한 번만 저장되고 오류도 나지 않는다")
    void addIsIdempotent() {
        Article article = publish("북마크 대상");

        bookmarkService.add(userId, article.getId());
        bookmarkService.add(userId, article.getId());
        em.flush();

        assertThat(countRows("bookmarks", article.getId())).isEqualTo(1);
        assertThat(counter(article.getId(), "bookmark_count"))
                .as("두 번째 요청이 카운터를 한 번 더 올리면 안 됩니다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("저장하지 않은 공지를 해제해도 오류가 아니다")
    void removeIsIdempotent() {
        Article article = publish("저장 안 한 공지");

        assertThatCode(() -> bookmarkService.remove(userId, article.getId()))
                .doesNotThrowAnyException();
        assertThat(counter(article.getId(), "bookmark_count")).isZero();
    }

    @Test
    @DisplayName("좋아요도 계정당 1표다")
    void likeIsIdempotent() {
        Article article = publish("좋아요 대상");

        likeService.like(userId, article.getId());
        likeService.like(userId, article.getId());
        em.flush();

        assertThat(countRows("article_likes", article.getId())).isEqualTo(1);
        assertThat(counter(article.getId(), "like_count")).isEqualTo(1);
    }

    @Test
    @DisplayName("해제하면 카운터도 함께 내려간다")
    void removeSyncsCounter() {
        Article article = publish("올렸다 내릴 공지");
        bookmarkService.add(userId, article.getId());
        likeService.like(userId, article.getId());
        em.flush();

        bookmarkService.remove(userId, article.getId());
        likeService.unlike(userId, article.getId());
        em.flush();

        assertThat(counter(article.getId(), "bookmark_count")).isZero();
        assertThat(counter(article.getId(), "like_count")).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 노출 기준
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 한 번도 배포된 적 없는 공지는 북마크할 수 없다 — 번호로 존재를 알아내지 못하게")
    void cannotBookmarkNeverPublishedArticle() {
        Article hidden = articleRepository.saveAndFlush(
                Article.createSchoolArticle("검수 대기 공지", "내용", null, null, null));

        assertThatThrownBy(() -> bookmarkService.add(userId, hidden.getId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> likeService.like(userId, hidden.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("없는 공지 번호도 404 다")
    void cannotBookmarkMissingArticle() {
        assertThatThrownBy(() -> bookmarkService.add(userId, 999_999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 재검수로 내려간 공지도 북마크 목록에는 남는다 — 오탈자 하나로 사라지면 안 된다")
    void bookmarkListKeepsArticleUnderReview() {
        Article article = publish("배포 후 재검수될 공지");
        bookmarkService.add(userId, article.getId());
        em.flush();

        article.changeStatus(ArticleStatus.PENDING_REVIEW);
        em.flush();

        List<ArticleSummaryResponse> bookmarks = list();

        assertThat(bookmarks).extracting(ArticleSummaryResponse::title)
                .containsExactly("배포 후 재검수될 공지");
        assertThat(bookmarks.get(0).underReview())
                .as("프론트가 '검수 중' 배지를 달려면 이 값이 필요합니다")
                .isTrue();
    }

    @Test
    @DisplayName("★ 재검수 공지는 일반 피드에는 나오지 않는다 — 북마크 목록만의 예외다")
    void feedStillHidesArticleUnderReview() {
        Article article = publish("재검수된 공지");
        bookmarkService.add(userId, article.getId());
        em.flush();
        article.changeStatus(ArticleStatus.PENDING_REVIEW);
        em.flush();

        assertThat(list()).hasSize(1);   // 북마크 목록에는 있고
        assertThat(feed()).isEmpty();    // 피드에는 없다
    }

    @Test
    @DisplayName("휴지통으로 간 공지는 북마크 목록에서도 사라진다")
    void bookmarkListHidesTrashedArticle() {
        Article article = publish("휴지통 갈 공지");
        bookmarkService.add(userId, article.getId());
        em.flush();

        article.changeStatus(ArticleStatus.TRASHED);
        em.flush();

        assertThat(list()).isEmpty();
    }

    @Test
    @DisplayName("보이지 않게 된 공지도 해제는 할 수 있다")
    void canRemoveBookmarkOfHiddenArticle() {
        Article article = publish("나중에 숨겨질 공지");
        bookmarkService.add(userId, article.getId());
        em.flush();
        article.changeStatus(ArticleStatus.TRASHED);
        em.flush();

        bookmarkService.remove(userId, article.getId());
        em.flush();

        assertThat(countRows("bookmarks", article.getId())).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BMK-04 전체 삭제
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("전체 삭제를 출처별로 할 수 있다")
    void removeAllBySourceType() {
        Article school = publish("학교 공지");
        Article club = publishClub("동아리 공지");
        bookmarkService.add(userId, school.getId());
        bookmarkService.add(userId, club.getId());
        em.flush();

        int removed = bookmarkService.removeAll(userId, SourceType.CLUB);
        em.flush();

        assertThat(removed).isEqualTo(1);
        assertThat(list()).extracting(ArticleSummaryResponse::title).containsExactly("학교 공지");
        assertThat(counter(club.getId(), "bookmark_count"))
                .as("일괄 삭제에도 트리거가 행마다 돌아야 합니다")
                .isZero();
    }

    @Test
    @DisplayName("출처를 지정하지 않으면 전부 지운다")
    void removeAllWithoutFilter() {
        bookmarkService.add(userId, publish("공지 1").getId());
        bookmarkService.add(userId, publish("공지 2").getId());
        em.flush();

        assertThat(bookmarkService.removeAll(userId, null)).isEqualTo(2);
        em.flush();
        assertThat(list()).isEmpty();
    }

    @Test
    @DisplayName("★ 전체 삭제가 남의 북마크를 건드리지 않는다")
    void removeAllTouchesOnlyMyBookmarks() {
        Article article = publish("공유 공지");
        Long otherUserId = insertUser("other@inha.ac.kr");
        bookmarkService.add(userId, article.getId());
        bookmarkService.add(otherUserId, article.getId());
        em.flush();

        bookmarkService.removeAll(userId, null);
        em.flush();

        assertThat(countRows("bookmarks", article.getId())).isEqualTo(1);
        assertThat(counter(article.getId(), "bookmark_count")).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 검증 규약이 피드와 같은가
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 피드와 북마크 목록이 같은 요청에 같은 판정을 내린다")
    void validationMatchesFeed() {
        // 마감 임박순은 양쪽 다 has_deadline=true 를 요구해야 합니다.
        assertThatThrownBy(() -> bookmarkService.list(
                userId, emptyCondition(), PageRequest.of(0, 20, Sort.by("ends_on"))))
                .as("한쪽만 통과하면 클라이언트가 같은 정렬 UI 를 두 화면에 못 씁니다")
                .isInstanceOf(BusinessException.class);

        // 한 글자 검색도 마찬가지입니다.
        assertThatThrownBy(() -> bookmarkService.list(
                userId,
                new ArticleSearchCondition(null, null, null, "학", false, null, null, null),
                PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 목록 내용
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("북마크 목록에는 내 북마크만 나오고 is_bookmarked 가 켜져 있다")
    void listShowsOnlyMine() {
        Article mine = publish("내가 저장한 공지");
        publish("저장 안 한 공지");
        bookmarkService.add(userId, mine.getId());
        em.flush();

        List<ArticleSummaryResponse> bookmarks = list();

        assertThat(bookmarks).hasSize(1);
        assertThat(bookmarks.get(0).isBookmarked()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<ArticleSummaryResponse> list() {
        return bookmarkService.list(userId, emptyCondition(), PageRequest.of(0, 20)).getContent();
    }

    private List<ArticleSummaryResponse> feed() {
        return articleQueryRepository.search(emptyCondition(), userId, PageRequest.of(0, 20)).getContent();
    }

    private ArticleSearchCondition emptyCondition() {
        return new ArticleSearchCondition(null, null, null, null, false, null, null, null);
    }

    private Article publish(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    private Article publishClub(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createClubArticle(title, "내용", null, null, null));
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '북마크테스터', 'USER', 'ACTIVE')")
                .setParameter("email", email).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }

    private int countRows(String table, Long articleId) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM " + table + " WHERE article_id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }

    private int counter(Long articleId, String column) {
        return ((Number) em.createNativeQuery(
                        "SELECT " + column + " FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }
}
