package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.global.security.JwtTokenProvider;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * ADM-16 회원 역할 관리.
 *
 * <p><b>이 기능의 값어치는 권한 변경 자체가 아니라 기록입니다.</b>
 * v1 에서는 DB 를 직접 고치는 것이 유일한 방법이라 "누가 언제 누구에게 관리자를 줬는지" 가
 * 아무 데도 남지 않았습니다. 그래서 여기서는 {@code user_role_logs} 에 <b>행위자까지</b>
 * 채워지는지를 확인합니다.
 *
 * <p>행위자는 {@code AuditAwareTransactionManager} 가 SecurityContext 에서 꺼내
 * 트랜잭션-로컬 GUC 로 넣습니다. 서비스를 직접 부르면 그 경로가 재현되지 않으므로
 * <b>인증 필터부터 태웁니다.</b> {@code @Transactional} 이 없는 것도 그래서입니다.
 */
@AutoConfigureMockMvc
class AdminUserRoleTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "role-admin@inha.ac.kr";
    private static final String TARGET_EMAIL = "role-target@inha.ac.kr";
    private static final String GONE_EMAIL = "role-gone@inha.ac.kr";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;
    private Long targetId;
    private Long withdrawnId;

    @BeforeEach
    void setUp() {
        cleanUp();
        transactionTemplate.executeWithoutResult(status -> {
            insert(ADMIN_EMAIL, "역할관리자", "ADMIN", false);
            insert(TARGET_EMAIL, "대상회원", "USER", false);
            insert(GONE_EMAIL, "탈퇴회원", "USER", true);
        });
        adminId = idOf(ADMIN_EMAIL);
        targetId = idOf(TARGET_EMAIL);
        withdrawnId = idOf(GONE_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery("DELETE FROM users WHERE email IN (:emails)")
                        .setParameter("emails", List.of(ADMIN_EMAIL, TARGET_EMAIL, GONE_EMAIL))
                        .executeUpdate());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 감사 기록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 권한을 바꾸면 누가 바꿨는지까지 이력에 남는다 — 이 기록이 기능의 존재 이유다")
    void roleChangeIsAuditedWithActor() throws Exception {
        changeRole(targetId, "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        Object[] log = lastRoleLog(targetId);
        assertThat(log[0]).isEqualTo("USER");
        assertThat(log[1]).isEqualTo("ADMIN");
        assertThat(((Number) log[2]).longValue())
                .as("changed_by 가 NULL 이면 '크롤러/시스템이 한 변경' 과 구분되지 않습니다")
                .isEqualTo(adminId);
    }

    @Test
    @DisplayName("★ 같은 권한을 다시 보내면 이력이 늘어나지 않는다")
    void repeatingTheSameRoleDoesNotAddNoise() throws Exception {
        changeRole(targetId, "ADMIN").andExpect(status().isOk());
        changeRole(targetId, "ADMIN").andExpect(status().isOk());

        assertThat(roleLogCount(targetId))
                .as("목록에서 실수로 한 번 더 눌렀다고 이력이 쌓이면 진짜 변경을 찾기 어려워집니다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("★ 같은 권한을 다시 보내는 것만으로 남의 세션이 끊기면 안 된다")
    void repeatingTheSameRoleDoesNotCutTheSession() throws Exception {
        // 대상은 USER 이고 아직 한 번도 무효화된 적이 없습니다.
        String targetToken = "Bearer " + jwtTokenProvider.createAccessToken(targetId, UserRole.USER);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, targetToken))
                .andExpect(status().isOk());

        // 이미 USER 인데 USER 로 다시 바꾸라는 요청 — 아무 일도 일어나면 안 됩니다.
        changeRole(targetId, "USER").andExpect(status().isOk());

        // ★ 이력 개수만 세는 검사로는 이 규칙을 확인할 수 없습니다.
        //   개수 1은 DB 트리거의 IS DISTINCT FROM 이 이미 보장하므로,
        //   서비스의 조기 반환이 사라져도 그대로 1입니다. 끊기는 것은 세션 쪽입니다.
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, targetToken))
                .andExpect(status().isOk());

        // 반대로 권한이 실제로 바뀌면 같은 토큰이 즉시 막혀야 합니다 — 두 분기가 구분됨을 함께 확인합니다.
        changeRole(targetId, "ADMIN").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, targetToken))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 거부 규칙
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 자기 자신의 권한은 못 바꾼다 — 관리자가 0명이 되면 되돌릴 방법이 없다")
    void cannotChangeOwnRole() throws Exception {
        changeRole(adminId, "USER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CANNOT_CHANGE_OWN_ROLE"));

        assertThat(roleOf(adminId)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("탈퇴한 회원에게는 관리자 권한을 줄 수 없다")
    void withdrawnUserCannotBePromoted() throws Exception {
        changeRole(withdrawnId, "ADMIN").andExpect(status().isBadRequest());

        assertThat(roleOf(withdrawnId)).isEqualTo("USER");
    }

    @Test
    @DisplayName("탈퇴한 관리자는 강등할 수 있다 — 안 그러면 목록에 영원히 남는다")
    void withdrawnAdminCanBeDemoted() throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery("UPDATE users SET role = 'ADMIN' WHERE id = :id")
                        .setParameter("id", withdrawnId).executeUpdate());

        changeRole(withdrawnId, "USER").andExpect(status().isOk());

        assertThat(roleOf(withdrawnId)).isEqualTo("USER");
    }

    @Test
    @DisplayName("없는 회원이면 404")
    void missingUserIsNotFound() throws Exception {
        changeRole(999_999_999L, "ADMIN")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 즉시 반영
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 강등된 관리자의 기존 토큰은 즉시 막힌다 — 아니면 1시간 동안 관리자로 남는다")
    void demotedAdminLosesAccessImmediately() throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery("UPDATE users SET role = 'ADMIN' WHERE id = :id")
                        .setParameter("id", targetId).executeUpdate());

        String targetToken = "Bearer " + jwtTokenProvider.createAccessToken(targetId, UserRole.ADMIN);

        // 강등 전에는 관리자 API 가 열립니다.
        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, targetToken))
                .andExpect(status().isOk());

        changeRole(targetId, "USER").andExpect(status().isOk());

        // 권한은 토큰 안에 박혀 있어서, 끊지 않으면 만료(1시간)까지 관리자로 통합니다.
        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, targetToken))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 목록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이메일 조각으로 회원을 찾는다")
    void searchesByEmailFragment() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", "role-target")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(targetId))
                .andExpect(jsonPath("$.data.content[0].email").value(TARGET_EMAIL));
    }

    @Test
    @DisplayName("권한으로 거른다")
    void filtersByRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", "role-")
                        .param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(adminId));
    }

    @Test
    @DisplayName("★ 모르는 정렬 기준을 보내도 500 이 나지 않는다")
    void unknownSortDoesNotBreakTheList() throws Exception {
        // Spring Data 는 요청한 정렬을 검증 없이 ORDER BY 뒤에 이어 붙입니다.
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("sort", "nonExistingProperty")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions changeRole(Long userId, String role)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/users/{id}/role", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"" + role + "\"}"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(adminId, UserRole.ADMIN);
    }

    /**
     * {@code withdrawn_at} 을 null 파라미터로 넘기지 않습니다.
     * native 쿼리의 null 바인딩은 드라이버가 타입을 못 정해 실패할 수 있어, 문장을 나눕니다.
     * (DB CHECK 가 {@code (status='WITHDRAWN') = (withdrawn_at IS NOT NULL)} 를 강제합니다)
     */
    private void insert(String email, String name, String role, boolean withdrawn) {
        String sql = withdrawn
                ? "INSERT INTO users (email, name, role, status, withdrawn_at) "
                        + "VALUES (:email, :name, :role, 'WITHDRAWN', now())"
                : "INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, :name, :role, 'ACTIVE')";
        em.createNativeQuery(sql)
                .setParameter("email", email)
                .setParameter("name", name)
                .setParameter("role", role)
                .executeUpdate();
    }

    private Long idOf(String email) {
        return transactionTemplate.execute(status ->
                ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                        .setParameter("email", email).getSingleResult()).longValue());
    }

    private String roleOf(Long userId) {
        return transactionTemplate.execute(status ->
                (String) em.createNativeQuery("SELECT role FROM users WHERE id = :id")
                        .setParameter("id", userId).getSingleResult());
    }

    /** {@code from_role, to_role, changed_by} */
    private Object[] lastRoleLog(Long userId) {
        return transactionTemplate.execute(status -> (Object[]) em.createNativeQuery("""
                        SELECT from_role, to_role, changed_by FROM user_role_logs
                         WHERE user_id = :id ORDER BY id DESC LIMIT 1
                        """)
                .setParameter("id", userId).getSingleResult());
    }

    private int roleLogCount(Long userId) {
        return transactionTemplate.execute(status ->
                ((Number) em.createNativeQuery(
                                "SELECT count(*) FROM user_role_logs WHERE user_id = :id")
                        .setParameter("id", userId).getSingleResult()).intValue());
    }
}
