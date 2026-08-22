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
        CommentResponse created = commentService.create(articleId, userId, "첫 댓글");
        em.flush();

        assertThat(created.content()).isEqualTo("첫 댓글");
        assertThat(created.author().name()).isEqualTo("댓쓴이");
        assertThat(created.isMine()).isTrue();
        assertThat(created.edited()).isFalse();
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("배포되지 않은 공지에는 댓글을 쓸 수 없다")
    void cannotCommentOnHiddenArticle() {
        Article hidden = articleRepository.saveAndFlush(
                Article.createSchoolArticle("검수 대기", "내용", null, null, null));

        assertThatThrownBy(() -> commentService.create(hidden.getId(), userId, "댓글"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("빈 내용과 1000자 초과는 거부한다")
    void contentLengthIsValidated() {
        assertThatThrownBy(() -> commentService.create(articleId, userId, "   "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> commentService.create(articleId, userId, "가".repeat(1001)))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("남의 댓글은 수정할 수 없다")
    void cannotEditOthersComment() {
        CommentResponse comment = commentService.create(articleId, userId, "내 댓글");
        em.flush();

        assertThatThrownBy(() -> commentService.update(comment.id(), otherUserId, "고침"))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 삭제 — 이 도메인의 핵심
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 댓글을 지우면 행째 사라진다 — 자리를 남기지 않는다")
    void deleteWithoutRepliesRemovesRow() {
        CommentResponse comment = commentService.create(articleId, userId, "혼자 있는 댓글");
        em.flush();

        commentService.delete(comment.id(), userId);
        em.flush();

        assertThat(rowCount()).isZero();
        assertThat(commentCount()).isZero();
    }

    @Test
    @DisplayName("이미 삭제된 댓글은 다시 지울 수 없다")
    void cannotDeleteTwice() {
        CommentResponse root = commentService.create(articleId, userId, "원댓글");
        em.flush();

        commentService.delete(root.id(), userId);
        em.flush();

        assertThatThrownBy(() -> commentService.delete(root.id(), userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("남의 댓글은 지울 수 없다")
    void cannotDeleteOthersComment() {
        CommentResponse comment = commentService.create(articleId, userId, "내 댓글");
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
        commentService.create(articleId, userId, "댓글");
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
        CommentResponse root = commentService.create(articleId, userId, "원댓글");
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
    @DisplayName("댓글은 시간순 평면 목록이다")
    void listIsFlatAndChronological() {
        commentService.create(articleId, userId, "1번");
        commentService.create(articleId, otherUserId, "2번");
        commentService.create(articleId, userId, "3번");
        em.flush();
        em.clear();

        Page<CommentResponse> page = commentService.list(articleId, userId, PageRequest.of(0, 20));

        assertThat(page.getTotalElements())
                .as("답글이 없으므로 페이징 단위가 곧 댓글입니다")
                .isEqualTo(3);
        assertThat(page.getContent()).extracting(CommentResponse::content)
                .containsExactly("1번", "2번", "3번");
    }

    @Test
    @DisplayName("★ 탈퇴한 사용자의 댓글은 남지만 이름은 가려진다")
    void withdrawnAuthorIsMasked() {
        commentService.create(articleId, otherUserId, "탈퇴할 사람의 댓글");
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
        commentService.create(articleId, userId, "내 댓글");
        commentService.create(articleId, otherUserId, "남의 댓글");
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
