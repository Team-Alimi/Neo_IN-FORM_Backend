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

    @PersistenceContext
    private EntityManager em;

    /** 알림을 받는 사용자. */
    private Long ownerId;

    /** 남의 알림을 건드리지 못하는지 확인할 다른 사용자. */
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("ntf-owner@inha.ac.kr", "알림받는사람");
        otherUserId = insertUser("ntf-other@inha.ac.kr", "다른사용자");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NTF-01 ~ 04
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("배지 개수는 안 읽은 것만 센다")
    void unreadCount() {
        givenTwoNotifications();

        assertThat(notificationService.unreadCount(ownerId)).isEqualTo(2);

        Long first = list(ownerId).get(0).id();
        notificationService.markRead(ownerId, first);
        em.flush();

        assertThat(notificationService.unreadCount(ownerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 남의 알림은 읽음 처리할 수 없다")
    void cannotReadOthersNotification() {
        givenTwoNotifications();
        Long othersNotification = list(ownerId).get(0).id();

        assertThatThrownBy(() -> notificationService.markRead(otherUserId, othersNotification))
                .isInstanceOf(BusinessException.class);
        assertThat(notificationService.unreadCount(ownerId))
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
        Long id = list(ownerId).get(0).id();

        notificationService.markRead(ownerId, id);
        em.flush();

        em.createNativeQuery(
                        "UPDATE notifications SET read_at = now() - interval '1 hour' WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        Object backdated = readAt(id);

        notificationService.markRead(ownerId, id);
        em.flush();

        assertThat(readAt(id))
                .as("다시 읽었다고 읽은 시각이 현재로 바뀌면 안 됩니다")
                .isEqualTo(backdated);
    }

    @Test
    @DisplayName("전체 읽음은 처리한 개수를 돌려주고, 두 번째는 0 이다")
    void markAllRead() {
        givenTwoNotifications();

        assertThat(notificationService.markAllRead(ownerId)).isEqualTo(2);
        em.flush();
        assertThat(notificationService.markAllRead(ownerId)).isZero();
        assertThat(notificationService.unreadCount(ownerId)).isZero();
    }

    @Test
    @DisplayName("전체 읽음이 남의 알림까지 건드리지 않는다")
    void markAllReadTouchesOnlyMine() {
        givenTwoNotifications();
        insertNotification(otherUserId, "남의 알림");

        notificationService.markAllRead(ownerId);
        em.flush();

        assertThat(notificationService.unreadCount(otherUserId)).isEqualTo(1);
    }

    @Test
    @DisplayName("목록은 최신순이다")
    void listIsNewestFirst() {
        insertNotification(ownerId, "먼저 온 알림");
        insertNotification(ownerId, "나중에 온 알림");

        // created_at 은 트랜잭션 고정이라 같은 값입니다. id DESC tie-breaker 가 순서를 정합니다.
        assertThat(list(ownerId)).extracting(NotificationResponse::title)
                .containsExactly("나중에 온 알림", "먼저 온 알림");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void givenTwoNotifications() {
        insertNotification(ownerId, "알림 1");
        insertNotification(ownerId, "알림 2");
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


    private Long insertUser(String email, String name) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, :name, 'USER', 'ACTIVE')")
                .setParameter("email", email).setParameter("name", name).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }
}
