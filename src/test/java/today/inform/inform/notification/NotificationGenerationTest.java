package today.inform.inform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import today.inform.inform.notification.dto.response.NotificationResponse;
import today.inform.inform.notification.repository.NotificationRepository;
import today.inform.inform.notification.service.NotificationScheduler;
import today.inform.inform.notification.service.NotificationService;
import today.inform.inform.support.IntegrationTest;

/**
 * SYS-03 마감 D-1 알림 생성 · SYS-04 오래된 알림 정리.
 *
 * <p><b>이 파일이 지키는 것은 "알림이 실제로 생긴다" 입니다.</b> 조회 API 4개는 처음부터
 * 동작했지만 만드는 쪽이 없어서 목록이 늘 비어 있었고, 오류가 없으니 아무도 몰랐습니다.
 * 생성 경로가 끊기면 똑같이 조용히 비어 가므로 여기서 잠급니다.
 *
 * <p>가장 중요한 두 가지는 <b>멱등성</b>과 <b>마감 연장 재발송</b>입니다.
 * 전자가 깨지면 배치가 두 번 돌 때 같은 알림이 쌓이고, 후자가 깨지면
 * 마감이 미뤄진 공지의 새 날짜를 사용자가 영영 모릅니다.
 */
@Transactional
class NotificationGenerationTest extends IntegrationTest {

    private static final String EXPECTED_TITLE = "마감이 하루 남았습니다";

    @Autowired
    private NotificationScheduler scheduler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    private Long userId;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        userId = insertUser("gen-user@inha.ac.kr", "활성사용자");
        tomorrow = LocalDate.now().plusDays(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SYS-03 생성
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 북마크한 공지가 내일 마감이면 알림이 생긴다 — 문구까지 고정한다")
    void createsForBookmarkedArticle() {
        Long articleId = publish("국가장학금 2차 신청 안내", tomorrow);
        bookmark(userId, articleId);

        scheduler.createDeadlineNotifications();
        em.clear();

        assertThat(list(userId)).singleElement().satisfies(notification -> {
            assertThat(notification.title()).isEqualTo(EXPECTED_TITLE);
            assertThat(notification.message()).isEqualTo(
                    "국가장학금 2차 신청 안내 — %d월 %d일 마감"
                            .formatted(tomorrow.getMonthValue(), tomorrow.getDayOfMonth()));
            assertThat(notification.articleId())
                    .as("눌렀을 때 공지로 이동해야 합니다")
                    .isEqualTo(articleId);
            assertThat(notification.read()).isFalse();
        });
        assertThat(notificationService.unreadCount(userId))
                .as("배지에도 떠야 합니다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("북마크하지 않은 사용자에게는 생기지 않는다")
    void skipsUsersWithoutBookmark() {
        publish("아무도 북마크 안 한 공지", tomorrow);

        scheduler.createDeadlineNotifications();

        assertThat(count(userId)).isZero();
    }

    @Test
    @DisplayName("★ 탈퇴한 사용자는 제외한다 — 탈퇴자에게 알림을 쌓지 않는다")
    void skipsWithdrawnUsers() {
        Long withdrawn = insertWithdrawnUser("gen-gone@inha.ac.kr");
        Long articleId = publish("탈퇴자도 북마크한 공지", tomorrow);
        bookmark(withdrawn, articleId);
        bookmark(userId, articleId);

        scheduler.createDeadlineNotifications();

        assertThat(count(withdrawn)).isZero();
        assertThat(count(userId)).as("활성 사용자는 받아야 합니다").isEqualTo(1);
    }

    @Test
    @DisplayName("발행 상태가 아닌 공지는 제외한다 — 내려간 공지의 마감을 알리지 않는다")
    void skipsUnpublishedArticles() {
        Long articleId = publish("내려갈 공지", tomorrow);
        bookmark(userId, articleId);
        em.createNativeQuery("UPDATE articles SET status = 'TRASHED' WHERE id = :id")
                .setParameter("id", articleId).executeUpdate();

        scheduler.createDeadlineNotifications();

        assertThat(count(userId)).isZero();
    }

    @Test
    @DisplayName("마감이 내일이 아니면 생기지 않는다 — 오늘·모레 모두")
    void onlyTomorrowsDeadlineCounts() {
        Long today = publish("오늘 마감", LocalDate.now());
        Long dayAfter = publish("모레 마감", LocalDate.now().plusDays(2));
        bookmark(userId, today);
        bookmark(userId, dayAfter);

        scheduler.createDeadlineNotifications();

        assertThat(count(userId)).isZero();
    }

    @Test
    @DisplayName("마감일이 없는 공지는 대상이 아니다")
    void skipsArticlesWithoutDeadline() {
        Long articleId = publish("마감 없는 공지", null);
        bookmark(userId, articleId);

        scheduler.createDeadlineNotifications();

        assertThat(count(userId)).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 멱등성 · 재발송
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 두 번 돌려도 1건이다 — 배치 재실행·다중 인스턴스가 안전해야 한다")
    void isIdempotent() {
        Long articleId = publish("두 번 돌릴 공지", tomorrow);
        bookmark(userId, articleId);

        scheduler.createDeadlineNotifications();
        int secondRun = notificationRepository.createDeadlineNotifications(tomorrow);

        assertThat(secondRun)
                .as("ON CONFLICT 가 흡수하므로 두 번째는 0건이어야 합니다")
                .isZero();
        assertThat(count(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 마감일이 연장되면 다시 보낸다 — dedup_key 가 마감일이기 때문")
    void resendsWhenDeadlineExtended() {
        Long articleId = publish("연장될 공지", tomorrow);
        bookmark(userId, articleId);
        scheduler.createDeadlineNotifications();
        assertThat(count(userId)).isEqualTo(1);

        LocalDate extended = tomorrow.plusDays(7);
        em.createNativeQuery("UPDATE articles SET ends_on = :ends WHERE id = :id")
                .setParameter("ends", extended).setParameter("id", articleId).executeUpdate();

        int created = notificationRepository.createDeadlineNotifications(extended);

        assertThat(created).isEqualTo(1);
        assertThat(count(userId))
                .as("날짜가 바뀐 것은 사용자가 다시 알아야 하는 정보입니다")
                .isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 수동 실행 (관리자 테스트용)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("수동 실행은 기준 마감일과 생성 건수를 돌려준다 — created: 0 의 원인을 짚을 수 있어야 한다")
    void manualRunReportsResult() {
        Long articleId = publish("수동 실행 대상", tomorrow);
        bookmark(userId, articleId);

        NotificationScheduler.DeadlineRun first = scheduler.runDeadlineNotifications();

        assertThat(first.deadline())
                .as("서버가 본 \"내일\" 을 호출한 쪽이 확인할 수 있어야 합니다")
                .isEqualTo(tomorrow);
        assertThat(first.created()).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 수동 실행을 여러 번 눌러도 안전하다 — 두 번째는 created: 0")
    void manualRunIsSafeToRepeat() {
        Long articleId = publish("여러 번 누를 공지", tomorrow);
        bookmark(userId, articleId);

        scheduler.runDeadlineNotifications();
        NotificationScheduler.DeadlineRun second = scheduler.runDeadlineNotifications();

        assertThat(second.created()).isZero();
        assertThat(count(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("수동 실행과 배치가 같은 결과를 낸다 — 코드 경로가 갈라지면 테스트가 운영을 대변하지 못한다")
    void manualRunMatchesScheduledRun() {
        Long articleId = publish("경로 비교용 공지", tomorrow);
        bookmark(userId, articleId);

        scheduler.createDeadlineNotifications();
        int afterScheduled = count(userId);
        NotificationScheduler.DeadlineRun manual = scheduler.runDeadlineNotifications();

        assertThat(afterScheduled).isEqualTo(1);
        assertThat(manual.created())
                .as("배치가 이미 만들었으므로 수동 실행은 0건이어야 합니다")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SYS-04 정리
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 30일이 지난 알림은 지우고 29일은 남긴다 — 경계가 어긋나면 하루치가 통째로 사라진다")
    void deletesOnlyExpiredNotifications() {
        insertNotificationAgedDays(userId, "31일 전", 31);
        insertNotificationAgedDays(userId, "29일 전", 29);
        em.flush();

        scheduler.deleteOldNotifications();
        em.clear();

        assertThat(list(userId))
                .extracting(NotificationResponse::title)
                .containsExactly("29일 전");
    }

    @Test
    @DisplayName("읽지 않은 알림도 30일이 지나면 지운다 — 한 달 전 마감 배지는 노이즈다")
    void deletesUnreadToo() {
        insertNotificationAgedDays(userId, "안 읽고 오래된 것", 40);
        em.flush();
        assertThat(notificationService.unreadCount(userId)).isEqualTo(1);

        scheduler.deleteOldNotifications();

        assertThat(count(userId)).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<NotificationResponse> list(Long id) {
        return notificationService.list(id, PageRequest.of(0, 20)).getContent();
    }

    private int count(Long id) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM notifications WHERE user_id = :id")
                .setParameter("id", id).getSingleResult()).intValue();
    }

    private Long publish(String title, LocalDate endsOn) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, endsOn, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article.getId();
    }

    private void bookmark(Long id, Long articleId) {
        em.createNativeQuery("INSERT INTO bookmarks (user_id, article_id) "
                        + "VALUES (:userId, :articleId)")
                .setParameter("userId", id).setParameter("articleId", articleId).executeUpdate();
        em.flush();
    }

    /** {@code created_at} 은 DEFAULT now() 라 엔티티로는 과거 시각을 만들 수 없습니다. */
    private void insertNotificationAgedDays(Long id, String title, int days) {
        em.createNativeQuery("""
                        INSERT INTO notifications
                               (user_id, type, dedup_key, title, message, created_at)
                        VALUES (:userId, 'DEADLINE_D1', :key, :title, '본문', :createdAt)
                        """)
                .setParameter("userId", id)
                .setParameter("key", "aged-" + days)
                .setParameter("title", title)
                .setParameter("createdAt", OffsetDateTime.now().minusDays(days))
                .executeUpdate();
    }

    private Long insertUser(String email, String name) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, :name, 'USER', 'ACTIVE')")
                .setParameter("email", email).setParameter("name", name).executeUpdate();
        return idOf(email);
    }

    /** {@code ck_users_withdrawn} 때문에 {@code withdrawn_at} 을 함께 넣어야 합니다. */
    private Long insertWithdrawnUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status, withdrawn_at) "
                        + "VALUES (:email, '탈퇴자', 'USER', 'WITHDRAWN', now())")
                .setParameter("email", email).executeUpdate();
        return idOf(email);
    }

    private Long idOf(String email) {
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }
}
