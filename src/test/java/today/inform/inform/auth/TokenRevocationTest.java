package today.inform.inform.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.global.security.JwtTokenProvider;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * 이미 발급된 Access Token 이 <b>만료 전에</b> 끊기는지 확인합니다.
 *
 * <p>Refresh Token 만 지우면 남은 Access Token 이 1시간 동안 그대로 통합니다.
 * 그러면 "모든 기기에서 로그아웃"(AUTH-04)이 한 시간 뒤에야 효력이 생기고,
 * 탈퇴한 사용자가 그 사이 댓글을 계속 남길 수 있습니다.
 *
 * <p>{@code @Transactional} 이 없습니다 — MockMvc 요청은 별도 트랜잭션에서 돌아
 * 커밋되지 않은 사용자를 보지 못합니다.
 */
@AutoConfigureMockMvc
class TokenRevocationTest extends IntegrationTest {

    private static final String EMAIL = "revocation@inha.ac.kr";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    private Long userId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM users WHERE email = :email")
                    .setParameter("email", EMAIL).executeUpdate();
            em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                            + "VALUES (:email, '무효화테스터', 'USER', 'ACTIVE')")
                    .setParameter("email", EMAIL).executeUpdate();
        });
        userId = transactionTemplate.execute(status ->
                ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                        .setParameter("email", EMAIL).getSingleResult()).longValue());
    }

    @Test
    @DisplayName("★ 전체 로그아웃 뒤에는 남은 Access Token 이 통하지 않는다")
    void accessTokenStopsWorkingAfterLogoutAll() throws Exception {
        String token = accessToken();

        // 끊기 전에는 통합니다.
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout/all").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("★ 탈퇴하면 남은 Access Token 도 즉시 끊긴다")
    void accessTokenStopsWorkingAfterWithdrawal() throws Exception {
        String token = accessToken();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        // 탈퇴만으로는 부족합니다 — 토큰이 만료까지 살아 있으면 댓글을 계속 쓸 수 있습니다.
        mockMvc.perform(post("/api/v1/articles/1/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"탈퇴 후에 쓴 댓글\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("무효화 이후에 발급된 토큰은 정상 동작한다 — 다시 로그인할 수 있어야 한다")
    void tokenIssuedAfterRevocationStillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout/all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken())))
                .andExpect(status().isOk());

        // 무효화 기준은 "시각" 이라 이후 발급분은 살아 있어야 합니다.
        // iat 가 초 단위라 같은 초는 막는 쪽으로 판정하므로(TokenRevocationStore 주석 참조)
        // 1초를 넘겨 발급합니다. 실제 사용자는 재로그인에 그 이상이 걸립니다.
        Thread.sleep(1100);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken())))
                .andExpect(status().isOk());
    }

    private String accessToken() {
        return jwtTokenProvider.createAccessToken(userId, UserRole.USER);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
