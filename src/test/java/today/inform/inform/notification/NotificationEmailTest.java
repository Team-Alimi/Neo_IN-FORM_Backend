package today.inform.inform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.mail.MailSender;
import today.inform.inform.notification.service.NotificationEmailPaths;
import today.inform.inform.notification.service.NotificationEmailSender;
import today.inform.inform.notification.service.NotificationScheduler;
import today.inform.inform.notification.service.UnsubscribeTokenSigner;
import today.inform.inform.support.FakeMailSender;
import today.inform.inform.support.IntegrationTest;

/**
 * NTF-05 알림 메일 발송과 수신거부.
 *
 * <h2>★ {@code @Transactional} 이 없습니다 — 일부러입니다</h2>
 * 이 기능의 설계 전체가 <b>트랜잭션 경계</b>에 걸려 있습니다. 조회는 한 번,
 * 발송은 트랜잭션 밖, 기록은 건당 즉시 커밋. 테스트를 {@code @Transactional} 로 감싸면
 * 그 셋이 전부 하나의 테스트 트랜잭션에 들어가 버려,
 * <b>실수로 루프 전체를 한 트랜잭션에 묶어도 테스트가 통과합니다.</b>
 * 그건 운영에서 "메일은 갔는데 기록이 롤백되어 다음 주기에 또 보내는" 사고가 되고,
 * 메일은 되돌릴 수 없습니다. 그래서 커밋을 실제로 일으키고 뒤에서 치웁니다.
 *
 * <h2>이 파일이 잠그는 것</h2>
 * <ul>
 *   <li>안 보내야 할 사람에게 안 보내는가 — 토글 off · 탈퇴자 · 기간 창 밖</li>
 *   <li>같은 메일을 두 번 보내지 않는가</li>
 *   <li>발송이 실패했을 때 <b>알림이 남고</b> 다음 주기에 재시도되는가</li>
 *   <li>수신거부 링크가 {@code GET} 프리페치로 발동하지 않는가</li>
 *   <li>위조·변조된 토큰이 거부되는가</li>
 * </ul>
 */
@AutoConfigureMockMvc
@Import(FakeMailSender.Config.class)
class NotificationEmailTest extends IntegrationTest {

    private static final String ON_EMAIL = "ntf05-on@inha.ac.kr";
    private static final String OFF_EMAIL = "ntf05-off@inha.ac.kr";
    private static final String GONE_EMAIL = "ntf05-gone@inha.ac.kr";
    private static final String ARTICLE_TITLE = "NTF05 국가장학금 2차 신청 안내";

    /** 메일 본문에서 수신거부 토큰만 뽑아냅니다. */
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");

    @Autowired
    private NotificationEmailSender sender;

    @Autowired
    private NotificationScheduler scheduler;

    @Autowired
    private UnsubscribeTokenSigner signer;

    @Autowired
    private FakeMailSender mail;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    private Long onUserId;
    private Long articleId;

    @BeforeEach
    void setUp() {
        mail.reset();
        tx.executeWithoutResult(status -> {
            onUserId = insertUser(ON_EMAIL, true);
            articleId = publishClosingTomorrow();
            bookmark(onUserId, articleId);
        });
    }

    @AfterEach
    void tearDown() {
        // 커밋을 실제로 일으키는 테스트라 직접 치웁니다. FK 때문에 순서가 있습니다.
        tx.executeWithoutResult(status -> {
            execute("DELETE FROM notifications WHERE user_id IN "
                    + "(SELECT id FROM users WHERE email LIKE 'ntf05-%')");
            execute("DELETE FROM bookmarks WHERE user_id IN "
                    + "(SELECT id FROM users WHERE email LIKE 'ntf05-%')");
            execute("DELETE FROM users WHERE email LIKE 'ntf05-%'");
            execute("DELETE FROM articles WHERE title LIKE 'NTF05%'");
        });
        mail.reset();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 발송
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 알림이 생기면 메일이 간다 — 수신자·제목·본문을 고정한다")
    void sendsMailForNewNotification() {
        createNotifications();

        NotificationEmailSender.EmailRun run = sender.sendPending();

        assertThat(run.sent()).isEqualTo(1);
        assertThat(run.failed()).isZero();
        assertThat(run.duplicated())
                .as("0 이 아니면 중복 발송이 일어났다는 뜻입니다")
                .isZero();

        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo(ON_EMAIL);
            assertThat(sent.subject())
                    .as("받은 편지함에서 어디서 온 메일인지 보여야 합니다")
                    .isEqualTo("[INFORM] 마감이 하루 남았습니다");
            assertThat(sent.body())
                    .as("본문 첫 줄은 알림 메시지 그대로입니다")
                    .startsWith(ARTICLE_TITLE + " — ");
            assertThat(sent.body())
                    .as("수신거부 링크가 있어야 합니다")
                    .contains(NotificationEmailPaths.UNSUBSCRIBE + "?token=");
        });
    }

    @Test
    @DisplayName("★★ 두 번 돌려도 같은 메일이 두 번 가지 않는다")
    void doesNotSendTwice() {
        createNotifications();

        sender.sendPending();
        NotificationEmailSender.EmailRun second = sender.sendPending();

        assertThat(mail.countTo(ON_EMAIL))
                .as("email_sent_at 이 찍혀 두 번째 실행의 대상에서 빠져야 합니다")
                .isEqualTo(1);
        assertThat(second.targeted()).isZero();
    }

    @Test
    @DisplayName("★★ 발송이 실패하면 알림은 남고 다음 주기에 재시도된다")
    void retriesAfterFailure() {
        createNotifications();
        mail.failWith(new MailSender.MailSendFailure("테스트 발송 실패", null));

        NotificationEmailSender.EmailRun failedRun = sender.sendPending();

        assertThat(failedRun.failed()).isEqualTo(1);
        assertThat(failedRun.sent()).isZero();
        assertThat(mail.sent()).isEmpty();
        assertThat(notificationCount(onUserId))
                .as("SES 실패가 알림 생성을 롤백하면 안 됩니다 — 인앱 알림은 남아야 합니다")
                .isEqualTo(1);
        assertThat(emailSentAt(onUserId))
                .as("실패한 발송에 email_sent_at 을 찍으면 그 알림은 영영 메일로 안 갑니다")
                .isNull();

        // 다음 주기
        mail.failWith(null);
        assertThat(sender.sendPending().sent())
                .as("같은 알림이 그대로 대상에 남아 있어야 합니다")
                .isEqualTo(1);
        assertThat(mail.countTo(ON_EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("메일 수신을 꺼 둔 사용자에게는 보내지 않는다")
    void skipsDisabledUser() {
        Long offUserId = tx.execute(status -> {
            Long id = insertUser(OFF_EMAIL, false);
            bookmark(id, articleId);
            return id;
        });
        createNotifications();

        sender.sendPending();

        assertThat(notificationCount(offUserId))
                .as("인앱 알림은 받습니다 — 끈 것은 메일뿐입니다")
                .isEqualTo(1);
        assertThat(mail.countTo(OFF_EMAIL)).isZero();
        assertThat(mail.countTo(ON_EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 사용자에게는 보내지 않는다 — 주소가 남아 있어도")
    void skipsWithdrawnUser() {
        tx.executeWithoutResult(status -> {
            Long goneId = insertWithdrawnUser(GONE_EMAIL);
            // 알림 생성 배치는 탈퇴자를 이미 거르므로, 발송 쪽 조건만 보려고 직접 꽂습니다.
            insertNotification(goneId, OffsetDateTime.now());
        });

        sender.sendPending();

        assertThat(mail.countTo(GONE_EMAIL)).isZero();
    }

    @Test
    @DisplayName("★★ 기간 창보다 오래된 알림은 메일로 보내지 않는다 — 소급 발송 방지")
    void skipsNotificationsOlderThanWindow() {
        // 수신을 꺼 둔 사용자의 알림은 email_sent_at 이 영원히 NULL 로 남습니다.
        // 창이 없으면 그 사용자가 나중에 토글을 켜는 순간 쌓인 것이 한꺼번에 날아갑니다.
        tx.executeWithoutResult(status ->
                insertNotification(onUserId, OffsetDateTime.now().minusDays(3)));

        NotificationEmailSender.EmailRun run = sender.sendPending();

        assertThat(run.targeted()).isZero();
        assertThat(mail.sent()).isEmpty();
        assertThat(emailSentAt(onUserId))
                .as("보내지도, 보냈다고 찍지도 않습니다 — 거짓 기록을 남기지 않습니다")
                .isNull();
    }

    @Test
    @DisplayName("MAIL_FROM 이 없으면(발송 비활성) 아무것도 보내지 않는다")
    void sendsNothingWhenDisabled() {
        createNotifications();
        mail.disable();

        NotificationEmailSender.EmailRun run = sender.sendPending();

        assertThat(run.targeted()).isZero();
        assertThat(mail.sent()).isEmpty();
        assertThat(emailSentAt(onUserId))
                .as("꺼져 있던 동안의 알림은 켜진 뒤에 보내져야 합니다")
                .isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 수신거부
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★★ GET 은 확인 페이지만 — 메일 클라이언트의 프리페치로 꺼지지 않는다")
    void getDoesNotUnsubscribe() throws Exception {
        String token = sendAndExtractToken();

        mockMvc.perform(get(NotificationEmailPaths.UNSUBSCRIBE).param("token", token))
                .andExpect(status().isOk());

        assertThat(emailEnabled(ON_EMAIL))
                .as("GET 으로 상태가 바뀌면 메일이 도착한 것만으로 알림이 꺼집니다")
                .isTrue();
    }

    @Test
    @DisplayName("★ POST 가 실제로 끈다 — 그 뒤로는 메일 대상에서 빠진다")
    void postUnsubscribes() throws Exception {
        String token = sendAndExtractToken();

        mockMvc.perform(post(NotificationEmailPaths.UNSUBSCRIBE).param("token", token))
                .andExpect(status().isOk());

        assertThat(emailEnabled(ON_EMAIL)).isFalse();

        // 링크가 "눌리기만" 하고 발송 조건과 이어지지 않으면 의미가 없습니다.
        mail.reset();
        tx.executeWithoutResult(status ->
                insertNotification(onUserId, OffsetDateTime.now()));
        sender.sendPending();

        assertThat(mail.countTo(ON_EMAIL))
                .as("수신거부가 발송 조건에 실제로 반영돼야 합니다")
                .isZero();
    }

    @Test
    @DisplayName("★★ 서명이 조작된 토큰은 거부된다")
    void rejectsForgedSignature() throws Exception {
        String token = sendAndExtractToken();
        // 서명 마지막 글자만 바꿉니다. 형식과 payload 는 그대로입니다.
        char last = token.charAt(token.length() - 1);
        String forged = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(post(NotificationEmailPaths.UNSUBSCRIBE).param("token", forged))
                .andExpect(status().isBadRequest());

        assertThat(emailEnabled(ON_EMAIL)).isTrue();
    }

    @Test
    @DisplayName("★★ 만료 시각을 늘려 붙인 토큰은 거부된다 — 서명이 만료까지 덮는다")
    void rejectsTamperedExpiry() throws Exception {
        String token = sendAndExtractToken();
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String userId = token.substring(0, token.indexOf('.'));
        // 원래 서명을 그대로 들고 만료만 먼 미래로 바꿔 붙입니다.
        String tampered = userId + ".99999999999." + signature;

        mockMvc.perform(post(NotificationEmailPaths.UNSUBSCRIBE).param("token", tampered))
                .andExpect(status().isBadRequest());

        assertThat(emailEnabled(ON_EMAIL)).isTrue();
    }

    @Test
    @DisplayName("토큰이 없거나 형식이 아니면 거부된다")
    void rejectsMissingOrMalformedToken() throws Exception {
        mockMvc.perform(get(NotificationEmailPaths.UNSUBSCRIBE))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(NotificationEmailPaths.UNSUBSCRIBE).param("token", "not-a-token"))
                .andExpect(status().isBadRequest());

        assertThat(emailEnabled(ON_EMAIL)).isTrue();
    }

    @Test
    @DisplayName("수신거부 경로는 로그인 없이 열려 있다")
    void unsubscribePathIsPublic() throws Exception {
        // 401 이 아니라 400 이어야 합니다 — 401 이면 SecurityConfig 등록이 빠진 것이고,
        // 메일을 받은 사람이 링크를 눌러도 아무것도 못 합니다.
        mockMvc.perform(get(NotificationEmailPaths.UNSUBSCRIBE).param("token", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("발급한 토큰은 그 사용자를 가리킨다")
    void signedTokenCarriesUserId() {
        assertThat(signer.isEnabled())
                .as("UNSUBSCRIBE_SECRET 이 없으면 이 파일의 수신거부 검증이 전부 무의미해집니다")
                .isTrue();
        assertThat(signer.verify(signer.sign(onUserId))).contains(onUserId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    /** 09시 배치와 같은 코드로 알림을 만듭니다. 자체 트랜잭션에서 커밋됩니다. */
    private void createNotifications() {
        scheduler.runDeadlineNotifications();
    }

    private String sendAndExtractToken() {
        createNotifications();
        sender.sendPending();
        String body = mail.sent().getFirst().body();
        Matcher matcher = TOKEN.matcher(body);
        assertThat(matcher.find()).as("본문에 수신거부 토큰이 있어야 합니다").isTrue();
        return matcher.group(1);
    }

    private Long insertUser(String email, boolean emailEnabled) {
        em.createNativeQuery("INSERT INTO users "
                        + "(email, name, role, status, email_notification_enabled) "
                        + "VALUES (:email, 'NTF05', 'USER', 'ACTIVE', :enabled)")
                .setParameter("email", email)
                .setParameter("enabled", emailEnabled)
                .executeUpdate();
        return idOf(email);
    }

    /** {@code ck_users_withdrawn} 때문에 {@code withdrawn_at} 을 함께 넣어야 합니다. */
    private Long insertWithdrawnUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status, withdrawn_at) "
                        + "VALUES (:email, 'NTF05탈퇴', 'USER', 'WITHDRAWN', now())")
                .setParameter("email", email)
                .executeUpdate();
        return idOf(email);
    }

    /**
     * 알림을 직접 꽂습니다.
     *
     * <p>{@code created_at} 은 {@code DEFAULT now()} 라 엔티티로는 과거 시각을 만들 수 없고,
     * 생성 배치는 탈퇴자를 이미 걸러서 "발송 쪽 조건만" 보려면 이 경로가 필요합니다.
     */
    private void insertNotification(Long userId, OffsetDateTime createdAt) {
        em.createNativeQuery("""
                        INSERT INTO notifications
                               (user_id, type, dedup_key, title, message, created_at)
                        VALUES (:userId, 'DEADLINE_D1', :key, '마감이 하루 남았습니다',
                                :message, :createdAt)
                        """)
                .setParameter("userId", userId)
                .setParameter("key", "ntf05-" + createdAt.toEpochSecond())
                .setParameter("message", ARTICLE_TITLE + " — 마감 임박")
                .setParameter("createdAt", createdAt)
                .executeUpdate();
    }

    private Long publishClosingTomorrow() {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(
                        ARTICLE_TITLE, "내용", null, LocalDate.now().plusDays(1), null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article.getId();
    }

    private void bookmark(Long userId, Long article) {
        em.createNativeQuery("INSERT INTO bookmarks (user_id, article_id) "
                        + "VALUES (:userId, :articleId)")
                .setParameter("userId", userId)
                .setParameter("articleId", article)
                .executeUpdate();
    }

    private int notificationCount(Long userId) {
        return tx.execute(status -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM notifications WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult()).intValue());
    }

    private OffsetDateTime emailSentAt(Long userId) {
        return tx.execute(status -> (OffsetDateTime) em.createNativeQuery(
                        "SELECT email_sent_at FROM notifications "
                                + "WHERE user_id = :id ORDER BY id LIMIT 1")
                .setParameter("id", userId).getSingleResult());
    }

    private boolean emailEnabled(String email) {
        return tx.execute(status -> (Boolean) em.createNativeQuery(
                        "SELECT email_notification_enabled FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult());
    }

    private Long idOf(String email) {
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }

    private void execute(String sql) {
        em.createNativeQuery(sql).executeUpdate();
    }
}
