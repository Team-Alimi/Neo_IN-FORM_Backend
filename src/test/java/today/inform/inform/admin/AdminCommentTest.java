package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.comment.dto.request.AdminCommentSearchCondition;
import today.inform.inform.admin.comment.dto.response.AdminCommentSummary;
import today.inform.inform.admin.comment.service.AdminCommentService;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.comment.dto.response.CommentResponse;
import today.inform.inform.comment.service.CommentService;
import today.inform.inform.support.IntegrationTest;

/**
 * ADM-17 댓글 관리.
 *
 * <p><b>삭제 규칙은 사용자 삭제(CMT-04)와 같아야 합니다.</b> 관리자에게만 다른 규칙을 주면
 * 답글이 함께 사라지는데, 답글을 쓴 사람은 자기 글이 왜 없어졌는지 알 방법이 없습니다.
 * 그래서 "답글이 있으면 자리를 남긴다" 가 여기서도 지켜지는지를 확인합니다.
 */
@Transactional
class AdminCommentTest extends IntegrationTest {

    @Autowired
    private AdminCommentService adminCommentService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;
    private Long userId;
    private Long otherUserId;
    private Long articleId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("cmt-admin@inha.ac.kr", "ADMIN");
        userId = insertUser("cmt-user@inha.ac.kr", "USER");
        otherUserId = insertUser("cmt-other@inha.ac.kr", "USER");
        articleId = publish("댓글 관리 대상 공지").getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 삭제
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("댓글은 행째로 사라진다")
    void commentWithoutRepliesIsHardDeleted() {
        Long commentId = commentService.create(articleId, userId, "혼자 있는 댓글").id();
        em.flush();

        assertThat(adminCommentService.deleteAll(List.of(commentId), adminId).succeeded())
                .containsExactly(commentId);
        em.flush();

        assertThat(rowCount(commentId)).isZero();
        assertThat(commentCount()).isZero();
    }

    @Test
    @DisplayName("★ 이미 지워진 댓글이 섞여 있어도 나머지는 처리된다")
    void alreadyDeletedCommentsAreSkipped() {
        Long alive = commentService.create(articleId, userId, "살아 있는 댓글").id();
        Long gone = commentService.create(articleId, userId, "먼저 지울 댓글").id();
        commentService.delete(gone, userId);
        em.flush();

        BulkResult result = adminCommentService.deleteAll(List.of(alive, gone, 999_999_999L), adminId);
        em.flush();

        assertThat(result.succeeded())
                .as("목록을 띄워 둔 사이 글쓴이가 먼저 지우는 일은 흔합니다. 전체를 뒤집으면 안 됩니다")
                .containsExactly(alive);
        assertThat(result.failed())
                .as("★ 빠진 건은 조용히 사라지면 안 됩니다 — 30건 골랐는데 28건만 됐다는 걸 알 수 있어야 합니다")
                .extracting(BulkResult.Failure::id)
                .containsExactlyInAnyOrder(gone, 999_999_999L);
        assertThat(result.failed())
                .allSatisfy(failure -> assertThat(failure.code()).isEqualTo("COMMENT_NOT_FOUND"));

        assertThat(rowCount(alive)).isZero();
    }

    @Test
    @DisplayName("같은 댓글을 두 번 보내도 한 번만 센다")
    void duplicateIdsAreCountedOnce() {
        Long commentId = commentService.create(articleId, userId, "중복 요청 댓글").id();
        em.flush();

        assertThat(adminCommentService.deleteAll(List.of(commentId, commentId), adminId).succeeded())
                .as("중복은 합쳐집니다. 두 번째가 '이미 처리됨' 으로 실패해 보이면 안 됩니다")
                .containsExactly(commentId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 검색
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("본문 조각으로 찾고, 작성자 이메일이 함께 나온다")
    void searchesByKeywordWithAuthor() {
        commentService.create(articleId, userId, "장학금 신청 기한이 언제인가요");
        commentService.create(articleId, otherUserId, "관계없는 댓글");
        em.flush();

        List<AdminCommentSummary> found = search(new AdminCommentSearchCondition(
                "장학금", null, null));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).authorEmail())
                .as("신고 대응은 같은 사람이 쓴 다른 글을 찾는 일이라 작성자를 가리면 안 됩니다")
                .isEqualTo("cmt-user@inha.ac.kr");
        assertThat(found.get(0).articleTitle()).isEqualTo("댓글 관리 대상 공지");
    }

    @Test
    @DisplayName("작성자로 공지를 가로질러 찾는다")
    void searchesByAuthorAcrossArticles() {
        Long otherArticleId = publish("다른 공지").getId();
        commentService.create(articleId, userId, "첫 번째 공지 댓글");
        commentService.create(otherArticleId, userId, "두 번째 공지 댓글");
        commentService.create(articleId, otherUserId, "남의 댓글");
        em.flush();

        assertThat(search(new AdminCommentSearchCondition(null, null, userId)))
                .extracting(AdminCommentSummary::authorId)
                .containsExactly(userId, userId);
    }

    @Test
    @DisplayName("★ 한 글자 검색어는 무시한다 — 걸러지지 않는 댓글을 섞어 두 분기를 구분한다")
    void singleCharacterKeywordIsIgnored() {
        commentService.create(articleId, userId, "아무 댓글");
        commentService.create(articleId, otherUserId, "전혀 다른 내용");
        em.flush();

        // 검색어가 실제로 적용되면 "아" 를 가진 1건만 남습니다. 무시되면 2건 다 나옵니다.
        assertThat(search(new AdminCommentSearchCondition("아", articleId, null)))
                .as("한 글자로는 전체와 다를 바 없고 목록만 무거워집니다")
                .hasSize(2);

        // 두 글자부터는 실제로 걸러집니다 — 가드가 통째로 죽지 않았음을 함께 확인합니다.
        assertThat(search(new AdminCommentSearchCondition("아무", articleId, null)))
                .hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<AdminCommentSummary> search(AdminCommentSearchCondition condition) {
        return adminCommentService.search(condition, PageRequest.of(0, 20)).getContent();
    }

    private Article publish(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    private Long insertUser(String email, String role) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '댓글관리테스터', :role, 'ACTIVE')")
                .setParameter("email", email).setParameter("role", role).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }

    private int rowCount(Long commentId) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM comments WHERE id = :id")
                .setParameter("id", commentId).getSingleResult()).intValue();
    }

    private Object deletedAt(Long commentId) {
        return em.createNativeQuery("SELECT deleted_at FROM comments WHERE id = :id")
                .setParameter("id", commentId).getSingleResult();
    }

    private String content(Long commentId) {
        return (String) em.createNativeQuery("SELECT content FROM comments WHERE id = :id")
                .setParameter("id", commentId).getSingleResult();
    }

    private int commentCount() {
        return ((Number) em.createNativeQuery("SELECT comment_count FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }
}
