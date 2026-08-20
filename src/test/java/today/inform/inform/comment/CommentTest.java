package today.inform.inform.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.comment.dto.response.CommentResponse;
import today.inform.inform.comment.entity.Comment;
import today.inform.inform.comment.repository.CommentRepository;
import today.inform.inform.comment.service.CommentService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.exception.SqlStateErrorMapper;
import today.inform.inform.support.IntegrationTest;

/**
 * CMT-01 ~ CMT-04.
 *
 * <p>핵심은 <b>삭제 정책</b>과 <b>1단계 답글 제한</b>입니다.
 * 둘 다 앱과 DB 가 나눠 맡고 있어서 실제 DB 위에서만 확인됩니다.
 */
@Transactional
class CommentTest extends IntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CommentRepository commentRepository;

    @PersistenceContext
    private EntityManager em;

    private Long userId;
    private Long otherUserId;
    private Long articleId;

    @BeforeEach
    void setUp() {
        userId = insertUser("commenter@inha.ac.kr", "댓쓴이");
        otherUserId = insertUser("other-commenter@inha.ac.kr", "남");
        articleId = publish("댓글 달릴 공지").getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 작성
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("댓글을 쓰면 공지의 댓글 수가 올라간다")
    void createIncrementsCount() {
        CommentResponse created = commentService.create(articleId, userId, "첫 댓글", null);
        em.flush();

        assertThat(created.content()).isEqualTo("첫 댓글");
        assertThat(created.author().name()).isEqualTo("댓쓴이");
        assertThat(created.isMine()).isTrue();
        assertThat(created.edited()).isFalse();
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("답글은 원댓글에만 달 수 있다")
    void replyToRootComment() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        CommentResponse reply = commentService.create(articleId, otherUserId, "답글", root.id());
        em.flush();

        assertThat(reply.id()).isNotNull();
        assertThat(commentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("★ 답글에 답글은 DB 가 막는다 — IN004")
    void replyToReplyIsRejected() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        CommentResponse reply = commentService.create(articleId, userId, "답글", root.id());
        em.flush();

        Throwable thrown = catchThrowable(() ->
                commentService.create(articleId, userId, "답글의 답글", reply.id()));

        assertThat(SqlStateErrorMapper.resolve(thrown))
                .as("트리거의 IN004 가 COMMENT_DEPTH_EXCEEDED 로 매핑되어야 400 이 됩니다")
                .isEqualTo(ErrorCode.COMMENT_DEPTH_EXCEEDED);
    }

    @Test
    @DisplayName("★ 다른 공지의 댓글을 상위로 지정할 수 없다 — IN005")
    void replyAcrossArticlesIsRejected() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        em.flush();
        Long otherArticleId = publish("다른 공지").getId();

        Throwable thrown = catchThrowable(() ->
                commentService.create(otherArticleId, userId, "엉뚱한 답글", root.id()));

        assertThat(SqlStateErrorMapper.resolve(thrown))
                .isEqualTo(ErrorCode.INVALID_COMMENT_PARENT);
    }

    @Test
    @DisplayName("배포되지 않은 공지에는 댓글을 쓸 수 없다")
    void cannotCommentOnHiddenArticle() {
        Article hidden = articleRepository.saveAndFlush(
                Article.createSchoolArticle("검수 대기", "내용", null, null, null));

        assertThatThrownBy(() -> commentService.create(hidden.getId(), userId, "댓글", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("빈 내용과 1000자 초과는 거부한다")
    void contentLengthIsValidated() {
        assertThatThrownBy(() -> commentService.create(articleId, userId, "   ", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> commentService.create(articleId, userId, "가".repeat(1001), null))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("남의 댓글은 수정할 수 없다")
    void cannotEditOthersComment() {
        CommentResponse comment = commentService.create(articleId, userId, "내 댓글", null);
        em.flush();

        assertThatThrownBy(() -> commentService.update(comment.id(), otherUserId, "고침"))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 삭제 — 이 도메인의 핵심
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 답글이 없으면 행째 지운다")
    void deleteWithoutRepliesRemovesRow() {
        CommentResponse comment = commentService.create(articleId, userId, "혼자 있는 댓글", null);
        em.flush();

        commentService.delete(comment.id(), userId);
        em.flush();

        assertThat(rowCount()).isZero();
        assertThat(commentCount()).isZero();
    }

    @Test
    @DisplayName("★ 답글이 있으면 자리를 남긴다 — 지우면 답글까지 CASCADE 로 사라진다")
    void deleteWithRepliesKeepsPlaceholder() {
        CommentResponse root = commentService.create(articleId, userId, "지울 원댓글", null);
        commentService.create(articleId, otherUserId, "살아남아야 할 답글", root.id());
        em.flush();

        commentService.delete(root.id(), userId);
        em.flush();
        em.clear();

        assertThat(rowCount()).as("원댓글 자리 + 답글").isEqualTo(2);
        assertThat(commentCount())
                .as("자리만 남은 댓글은 개수에서 빠집니다")
                .isEqualTo(1);

        Page<CommentResponse> page = commentService.list(articleId, userId, PageRequest.of(0, 20));
        CommentResponse placeholder = page.getContent().get(0);

        assertThat(placeholder.deleted()).isTrue();
        assertThat(placeholder.content()).as("본문은 내보내지 않습니다").isNull();
        assertThat(placeholder.author()).as("지운 댓글의 작성자도 남기지 않습니다").isNull();
        assertThat(placeholder.replies()).hasSize(1);
        assertThat(placeholder.replies().get(0).content()).isEqualTo("살아남아야 할 답글");
    }

    @Test
    @DisplayName("이미 삭제된 댓글은 다시 지울 수 없다")
    void cannotDeleteTwice() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        commentService.create(articleId, userId, "답글", root.id());
        em.flush();

        commentService.delete(root.id(), userId);
        em.flush();

        assertThatThrownBy(() -> commentService.delete(root.id(), userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("남의 댓글은 지울 수 없다")
    void cannotDeleteOthersComment() {
        CommentResponse comment = commentService.create(articleId, userId, "내 댓글", null);
        em.flush();

        assertThatThrownBy(() -> commentService.delete(comment.id(), otherUserId))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 회귀 방지
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 클라이언트가 보낸 정렬은 무시된다 — 그대로 쓰면 JPQL 에 섞여 500 이 난다")
    void clientSortIsIgnored() {
        commentService.create(articleId, userId, "댓글", null);
        em.flush();

        assertThatCode(() -> commentService.list(
                articleId, userId, PageRequest.of(0, 20, Sort.by("존재하지않는속성"))))
                .as("Spring Data 가 @Query 의 ORDER BY 뒤에 그대로 이어 붙입니다")
                .doesNotThrowAnyException();
    }

    /**
     * <b>동시 실행이라야 드러나는 문제입니다.</b>
     *
     * <p>순차로 부르면 {@code findEditable} 이 다시 읽으면서 삭제 상태를 보고 막습니다.
     * 실제로 깨지는 건 수정 쪽이 <b>삭제 전에 이미 엔티티를 들고 있는</b> 경우입니다 —
     * 그때는 낡은 {@code deleted_at = NULL} 이 UPDATE 문에 그대로 실려 나갑니다.
     *
     * <p>다른 트랜잭션의 soft delete 를 native UPDATE 로 흉내 냅니다.
     * 실제 {@code softDelete()} 도 JPA 변경이라 version 을 올리므로 조건이 같습니다.
     */
    @Test
    @DisplayName("★ 수정이 다른 트랜잭션의 삭제를 되돌리지 않는다")
    void editDoesNotResurrectDeletedComment() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글", null);
        commentService.create(articleId, otherUserId, "답글", root.id());
        em.flush();
        em.clear();

        // 수정 트랜잭션이 삭제 전에 엔티티를 들고 있는 상태
        Comment loaded = commentRepository.findById(root.id()).orElseThrow();

        // 그 사이 다른 트랜잭션이 자리를 남기며 지웁니다
        em.createNativeQuery("""
                        UPDATE comments SET deleted_at = now(), content = '', version = version + 1
                         WHERE id = :id
                        """)
                .setParameter("id", root.id())
                .executeUpdate();

        loaded.edit("되살아난 본문");

        assertThatThrownBy(() -> em.flush())
                .as("version 이 없으면 1행이 갱신되어 지운 댓글이 새 본문을 달고 되살아납니다")
                .isInstanceOf(OptimisticLockException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 목록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("원댓글은 시간순이고 답글은 그 아래 중첩된다")
    void listNestsReplies() {
        CommentResponse first = commentService.create(articleId, userId, "1번", null);
        commentService.create(articleId, userId, "2번", null);
        commentService.create(articleId, otherUserId, "1번의 답글", first.id());
        em.flush();
        em.clear();

        Page<CommentResponse> page = commentService.list(articleId, userId, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).as("페이징 단위는 원댓글입니다").isEqualTo(2);
        assertThat(page.getContent()).extracting(CommentResponse::content)
                .containsExactly("1번", "2번");
        assertThat(page.getContent().get(0).replies()).extracting(CommentResponse::content)
                .containsExactly("1번의 답글");
        assertThat(page.getContent().get(1).replies()).isEmpty();
    }

    @Test
    @DisplayName("★ 탈퇴한 사용자의 댓글은 남지만 이름은 가려진다")
    void withdrawnAuthorIsMasked() {
        commentService.create(articleId, otherUserId, "탈퇴할 사람의 댓글", null);
        em.flush();

        em.createNativeQuery("UPDATE users SET status='WITHDRAWN', withdrawn_at=now() WHERE id=:id")
                .setParameter("id", otherUserId).executeUpdate();
        em.clear();

        CommentResponse comment = commentService.list(articleId, userId, PageRequest.of(0, 20))
                .getContent().get(0);

        assertThat(comment.content()).as("댓글은 남습니다 — 지우면 스레드가 무너집니다")
                .isEqualTo("탈퇴할 사람의 댓글");
        assertThat(comment.author().name()).isEqualTo("탈퇴한 사용자");
        assertThat(comment.author().id()).as("탈퇴 계정으로 연결되면 안 됩니다").isNull();
    }

    @Test
    @DisplayName("is_mine 은 보는 사람 기준이다")
    void isMineFollowsViewer() {
        commentService.create(articleId, userId, "내 댓글", null);
        commentService.create(articleId, otherUserId, "남의 댓글", null);
        em.flush();
        em.clear();

        assertThat(commentService.list(articleId, userId, PageRequest.of(0, 20)).getContent())
                .extracting(CommentResponse::isMine)
                .containsExactly(true, false);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private Article publish(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    private Long insertUser(String email, String name) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, :name, 'USER', 'ACTIVE')")
                .setParameter("email", email).setParameter("name", name).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }

    private int commentCount() {
        return ((Number) em.createNativeQuery("SELECT comment_count FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }

    private int rowCount() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM comments WHERE article_id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }
}
