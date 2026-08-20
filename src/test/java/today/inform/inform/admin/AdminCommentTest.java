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
import today.inform.inform.admin.comment.dto.request.AdminCommentSearchCondition;
import today.inform.inform.admin.comment.dto.response.AdminCommentSummary;
import today.inform.inform.admin.comment.service.AdminCommentService;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
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
    @DisplayName("답글 없는 댓글은 행째로 사라진다")
    void commentWithoutRepliesIsHardDeleted() {
        Long commentId = commentService.create(articleId, userId, "혼자 있는 댓글", null).id();
        em.flush();

        assertThat(adminCommentService.deleteAll(List.of(commentId), adminId)).isEqualTo(1);
        em.flush();

        assertThat(rowCount(commentId)).isZero();
        assertThat(commentCount()).isZero();
    }

    @Test
    @DisplayName("★ 답글이 달린 원댓글을 지워도 답글은 남는다 — 관리자만 다르게 동작하면 안 된다")
    void rootWithRepliesKeepsItsPlace() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        Long replyId = commentService.create(articleId, otherUserId, "답글", root.id()).id();
        em.flush();

        adminCommentService.deleteAll(List.of(root.id()), adminId);
        em.flush();
        em.clear();

        assertThat(rowCount(root.id()))
                .as("행을 지우면 ON DELETE CASCADE 로 남의 답글까지 조용히 사라집니다")
                .isEqualTo(1);
        assertThat(rowCount(replyId)).isEqualTo(1);
        assertThat(deletedAt(root.id())).isNotNull();
        assertThat(content(root.id()))
                .as("자리만 남기고 본문은 비웁니다")
                .isEmpty();
        assertThat(commentCount())
                .as("자리만 남은 댓글은 개수에서 빠집니다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("★ 글타래를 통째로 고르면 답글이 먼저 지워져 껍데기가 남지 않는다")
    void deletingWholeThreadLeavesNoStub() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        Long replyId = commentService.create(articleId, otherUserId, "답글", root.id()).id();
        em.flush();

        // 원댓글을 먼저 넣어 보냅니다 — 순서를 서비스가 다시 정하지 않으면
        // 원댓글이 '답글 있음' 으로 판정돼 빈 껍데기가 남습니다.
        assertThat(adminCommentService.deleteAll(List.of(root.id(), replyId), adminId)).isEqualTo(2);
        em.flush();
        em.clear();

        assertThat(rowCount(root.id())).isZero();
        assertThat(rowCount(replyId)).isZero();
        assertThat(commentCount()).isZero();
    }

    @Test
    @DisplayName("★ 이미 지워진 댓글이 섞여 있어도 나머지는 처리된다")
    void alreadyDeletedCommentsAreSkipped() {
        Long alive = commentService.create(articleId, userId, "살아 있는 댓글", null).id();
        Long gone = commentService.create(articleId, userId, "먼저 지울 댓글", null).id();
        commentService.delete(gone, userId);
        em.flush();

        assertThat(adminCommentService.deleteAll(List.of(alive, gone, 999_999_999L), adminId))
                .as("목록을 띄워 둔 사이 글쓴이가 먼저 지우는 일은 흔합니다. 전체를 뒤집으면 안 됩니다")
                .isEqualTo(1);
        em.flush();

        assertThat(rowCount(alive)).isZero();
    }

    @Test
    @DisplayName("같은 댓글을 두 번 보내도 한 번만 센다")
    void duplicateIdsAreCountedOnce() {
        Long commentId = commentService.create(articleId, userId, "중복 요청 댓글", null).id();
        em.flush();

        assertThat(adminCommentService.deleteAll(List.of(commentId, commentId), adminId)).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 검색
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("본문 조각으로 찾고, 작성자 이메일이 함께 나온다")
    void searchesByKeywordWithAuthor() {
        commentService.create(articleId, userId, "장학금 신청 기한이 언제인가요", null);
        commentService.create(articleId, otherUserId, "관계없는 댓글", null);
        em.flush();

        List<AdminCommentSummary> found = search(new AdminCommentSearchCondition(
                "장학금", null, null, false));

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
        commentService.create(articleId, userId, "첫 번째 공지 댓글", null);
        commentService.create(otherArticleId, userId, "두 번째 공지 댓글", null);
        commentService.create(articleId, otherUserId, "남의 댓글", null);
        em.flush();

        assertThat(search(new AdminCommentSearchCondition(null, null, userId, false)))
                .extracting(AdminCommentSummary::authorId)
                .containsExactly(userId, userId);
    }

    @Test
    @DisplayName("삭제된 댓글은 기본으로 빠지고, 요청하면 나온다")
    void deletedCommentsAreHiddenByDefault() {
        CommentResponse root = commentService.create(articleId, userId, "지울 원댓글", null);
        commentService.create(articleId, otherUserId, "답글", root.id());
        commentService.delete(root.id(), userId);
        em.flush();

        assertThat(search(new AdminCommentSearchCondition(null, articleId, null, false)))
                .extracting(AdminCommentSummary::id)
                .doesNotContain(root.id());

        assertThat(search(new AdminCommentSearchCondition(null, articleId, null, true)))
                .extracting(AdminCommentSummary::id)
                .contains(root.id());
    }

    @Test
    @DisplayName("답글 수가 함께 나온다 — 지우면 자리가 남는지 화면이 미리 알 수 있다")
    void replyCountTellsWhetherDeletionLeavesAStub() {
        CommentResponse root = commentService.create(articleId, userId, "답글 달릴 댓글", null);
        commentService.create(articleId, otherUserId, "답글 하나", root.id());
        em.flush();

        AdminCommentSummary summary = search(new AdminCommentSearchCondition(null, articleId, null, false))
                .stream().filter(row -> row.id().equals(root.id())).findFirst().orElseThrow();

        assertThat(summary.replyCount()).isEqualTo(1);
        assertThat(summary.isReply()).isFalse();
    }

    @Test
    @DisplayName("★ 한 글자 검색어는 무시한다 — 걸러지지 않는 댓글을 섞어 두 분기를 구분한다")
    void singleCharacterKeywordIsIgnored() {
        commentService.create(articleId, userId, "아무 댓글", null);
        commentService.create(articleId, otherUserId, "전혀 다른 내용", null);
        em.flush();

        // 검색어가 실제로 적용되면 "아" 를 가진 1건만 남습니다. 무시되면 2건 다 나옵니다.
        assertThat(search(new AdminCommentSearchCondition("아", articleId, null, false)))
                .as("한 글자로는 전체와 다를 바 없고 목록만 무거워집니다")
                .hasSize(2);

        // 두 글자부터는 실제로 걸러집니다 — 가드가 통째로 죽지 않았음을 함께 확인합니다.
        assertThat(search(new AdminCommentSearchCondition("아무", articleId, null, false)))
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
