package today.inform.inform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.comment.dto.response.CommentResponse;
import today.inform.inform.comment.service.CommentService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.notification.dto.response.NotificationResponse;
import today.inform.inform.notification.entity.NotificationType;
import today.inform.inform.notification.service.NotificationService;
import today.inform.inform.support.IntegrationTest;

/**
 * NTF-01 ~ NTF-04 와 CMT-05(답글 알림).
 *
 * <p>알림은 사용자가 만들 수 없는 자원이라, 검증할 것은
 * <b>누구에게 만들어지는가</b>와 <b>남의 것을 건드릴 수 없는가</b> 두 가지입니다.
 */
@Transactional
class NotificationTest extends IntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    private Long authorId;
    private Long replierId;
    private Long articleId;

    @BeforeEach
    void setUp() {
        authorId = insertUser("ntf-author@inha.ac.kr", "원댓글쓴이");
        replierId = insertUser("ntf-replier@inha.ac.kr", "답글쓴이");
        articleId = publish("알림 테스트 공지").getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CMT-05 답글 알림
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 남이 내 댓글에 답글을 달면 알림이 온다")
    void replyCreatesNotification() {
        CommentResponse root = commentService.create(articleId, authorId, "원댓글", null);
        commentService.create(articleId, replierId, "답글입니다", root.id());
        em.flush();

        List<NotificationResponse> received = list(authorId);

        assertThat(received).hasSize(1);
        NotificationResponse notification = received.get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.COMMENT_REPLY);
        assertThat(notification.articleId()).isEqualTo(articleId);
        assertThat(notification.message()).contains("알림 테스트 공지").contains("답글입니다");
        assertThat(notification.read()).isFalse();

        assertThat(list(replierId)).as("답글 쓴 사람에게는 오지 않습니다").isEmpty();
    }

    @Test
    @DisplayName("★ 내 댓글에 내가 답글을 달면 알림이 오지 않는다")
    void selfReplyDoesNotNotify() {
        CommentResponse root = commentService.create(articleId, authorId, "원댓글", null);
        commentService.create(articleId, authorId, "자문자답", root.id());
        em.flush();

        assertThat(list(authorId)).isEmpty();
    }

    @Test
    @DisplayName("원댓글에는 알림이 생기지 않는다 — 알릴 대상이 없다")
    void rootCommentDoesNotNotify() {
        commentService.create(articleId, replierId, "그냥 원댓글", null);
        em.flush();

        assertThat(list(authorId)).isEmpty();
    }

    @Test
    @DisplayName("★ 탈퇴한 사용자에게는 알림을 만들지 않는다 — 읽을 수 없는 행이다")
    void withdrawnUserGetsNoNotification() {
        CommentResponse root = commentService.create(articleId, authorId, "원댓글", null);
        em.flush();

        em.createNativeQuery("UPDATE users SET status='WITHDRAWN', withdrawn_at=now() WHERE id=:id")
                .setParameter("id", authorId).executeUpdate();

        commentService.create(articleId, replierId, "답글", root.id());
        em.flush();

        assertThat(rowCount(authorId)).isZero();
    }

    @Test
    @DisplayName("★ 같은 답글로 알림이 두 번 생기지 않는다 — 유니크 인덱스가 막는다")
    void duplicateNotificationIsIgnored() {
        CommentResponse root = commentService.create(articleId, authorId, "원댓글", null);
        CommentResponse reply = commentService.create(articleId, replierId, "답글", root.id());
        em.flush();

        // 재시도를 흉내 냅니다. ON CONFLICT DO NOTHING 이라 조용히 넘어가야 합니다.
        notificationService.notifyReply(root.id(), reply.id(), replierId, "제목", "내용");
        em.flush();

        assertThat(rowCount(authorId)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 원댓글에는 알림을 만들지 않는다")
    void deletedParentGetsNoNotification() {
        CommentResponse root = commentService.create(articleId, authorId, "지울 원댓글", null);
        commentService.create(articleId, replierId, "첫 답글", root.id());
        em.flush();
        commentService.delete(root.id(), authorId);   // 답글이 있어 자리만 남습니다
        em.flush();

        int before = rowCount(authorId);
        commentService.create(articleId, replierId, "두 번째 답글", root.id());
        em.flush();

        assertThat(rowCount(authorId))
                .as("지운 댓글에 대한 알림은 열어 봐도 볼 것이 없습니다")
                .isEqualTo(before);
    }

    /**
     * 미리보기를 UTF-16 코드 단위로 자르면 이모지가 두 조각으로 갈라집니다.
     *
     * <p>짝을 잃은 조각은 UTF-8 로 인코딩할 수 없어 <b>드라이버가 조용히 {@code '?'} 로 바꿉니다.</b>
     * 예외가 나지 않아서 깨진 글자가 그대로 저장되고, 로그에도 아무것도 남지 않습니다.
     */
    @Test
    @DisplayName("★ 알림 미리보기가 이모지를 반토막 내지 않는다")
    void previewDoesNotSplitEmoji() {
        CommentResponse root = commentService.create(articleId, authorId, "원댓글", null);

        // 자르는 경계(60)가 이모지 한가운데 떨어지도록 맞춥니다.
        String thumbsUp = new String(Character.toChars(0x1F44D));
        String reply = "가".repeat(59) + thumbsUp + " 뒤에 더 있는 내용";

        commentService.create(articleId, replierId, reply, root.id());
        em.flush();

        String message = (String) em.createNativeQuery(
                        "SELECT message FROM notifications WHERE user_id = :id")
                .setParameter("id", authorId).getSingleResult();

        assertThat(message)
                .as("짝 없는 서로게이트는 인코딩 과정에서 '?' 로 치환됩니다")
                .doesNotContain("?");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NTF-01 ~ 04
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("배지 개수는 안 읽은 것만 센다")
    void unreadCount() {
        givenTwoNotifications();

        assertThat(notificationService.unreadCount(authorId)).isEqualTo(2);

        Long first = list(authorId).get(0).id();
        notificationService.markRead(authorId, first);
        em.flush();

        assertThat(notificationService.unreadCount(authorId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 남의 알림은 읽음 처리할 수 없다")
    void cannotReadOthersNotification() {
        givenTwoNotifications();
        Long othersNotification = list(authorId).get(0).id();

        assertThatThrownBy(() -> notificationService.markRead(replierId, othersNotification))
                .isInstanceOf(BusinessException.class);
        assertThat(notificationService.unreadCount(authorId))
                .as("남이 대신 읽어 준 것이 되면 안 됩니다")
                .isEqualTo(2);
    }

    /**
     * <b>읽은 시각을 과거로 밀어 두고 시작합니다.</b>
     *
     * <p>{@code now()} 는 트랜잭션 시작 시각으로 고정입니다. 테스트 전체가 한 트랜잭션이라
     * 그냥 두 번 호출하면 {@code COALESCE} 를 지워도 <b>같은 값</b>이 나와 테스트가 통과합니다.
     * 회귀를 잡지 못하는 테스트가 됩니다.
     *
     * <p>과거 시각을 넣어 두면 {@code COALESCE} 가 사라지는 순간
     * 현재 시각으로 덮어써져 바로 드러납니다.
     */
    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 최초 읽은 시각이 유지된다")
    void markReadPreservesFirstReadTime() {
        givenTwoNotifications();
        Long id = list(authorId).get(0).id();

        notificationService.markRead(authorId, id);
        em.flush();

        em.createNativeQuery(
                        "UPDATE notifications SET read_at = now() - interval '1 hour' WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        Object backdated = readAt(id);

        notificationService.markRead(authorId, id);
        em.flush();

        assertThat(readAt(id))
                .as("다시 읽었다고 읽은 시각이 현재로 바뀌면 안 됩니다")
                .isEqualTo(backdated);
    }

    @Test
    @DisplayName("전체 읽음은 처리한 개수를 돌려주고, 두 번째는 0 이다")
    void markAllRead() {
        givenTwoNotifications();

        assertThat(notificationService.markAllRead(authorId)).isEqualTo(2);
        em.flush();
        assertThat(notificationService.markAllRead(authorId)).isZero();
        assertThat(notificationService.unreadCount(authorId)).isZero();
    }

    @Test
    @DisplayName("전체 읽음이 남의 알림까지 건드리지 않는다")
    void markAllReadTouchesOnlyMine() {
        givenTwoNotifications();
        insertNotification(replierId, "남의 알림");

        notificationService.markAllRead(authorId);
        em.flush();

        assertThat(notificationService.unreadCount(replierId)).isEqualTo(1);
    }

    @Test
    @DisplayName("목록은 최신순이다")
    void listIsNewestFirst() {
        insertNotification(authorId, "먼저 온 알림");
        insertNotification(authorId, "나중에 온 알림");

        // created_at 은 트랜잭션 고정이라 같은 값입니다. id DESC tie-breaker 가 순서를 정합니다.
        assertThat(list(authorId)).extracting(NotificationResponse::title)
                .containsExactly("나중에 온 알림", "먼저 온 알림");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void givenTwoNotifications() {
        insertNotification(authorId, "알림 1");
        insertNotification(authorId, "알림 2");
    }

    private List<NotificationResponse> list(Long userId) {
        return notificationService.list(userId, PageRequest.of(0, 20)).getContent();
    }

    /** 공지와 무관한 알림. dedup 유니크 인덱스가 article_id IS NOT NULL 조건이라 자유롭게 넣을 수 있습니다. */
    private void insertNotification(Long userId, String title) {
        em.createNativeQuery("""
                        INSERT INTO notifications (user_id, type, dedup_key, title, message)
                        VALUES (:userId, 'DEADLINE_D1', '', :title, '본문')
                        """)
                .setParameter("userId", userId).setParameter("title", title)
                .executeUpdate();
    }

    private int rowCount(Long userId) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM notifications WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult()).intValue();
    }

    private Object readAt(Long notificationId) {
        return em.createNativeQuery("SELECT read_at FROM notifications WHERE id = :id")
                .setParameter("id", notificationId).getSingleResult();
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
}
