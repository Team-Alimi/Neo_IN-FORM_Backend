package today.inform.inform.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.global.security.JwtTokenProvider;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * 감사 로그의 <b>행위자</b>가 실제 요청 경로에서 채워지는지 확인합니다.
 *
 * <p>서비스를 직접 부르는 테스트로는 확인할 수 없습니다 — 행위자는
 * {@code AuditAwareTransactionManager} 가 <b>트랜잭션 시작 시점에</b> SecurityContext 에서 꺼내
 * DB 세션 변수로 넣기 때문입니다. 인증 필터부터 태워야 그 경로가 재현됩니다.
 *
 * <p>빠뜨리면 오류가 나지 않습니다. {@code changed_by} 가 NULL 로 남는데
 * 그건 스키마상 "크롤러/시스템이 한 변경" 의 정상값이라
 * <b>사후에 사고와 정상을 구분할 수 없습니다.</b> 그래서 따로 확인해 둡니다.
 *
 * <p>{@code @Transactional} 이 없습니다 — MockMvc 요청은 별도 트랜잭션에서 돕니다.
 */
@AutoConfigureMockMvc
class AdminAuditTest extends IntegrationTest {

    private static final String EMAIL = "admin-audit@inha.ac.kr";
    private static final String TITLE = "감사 테스트 공지";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;
    private Long articleId;

    @BeforeEach
    void setUp() {
        cleanUp();
        transactionTemplate.executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                            + "VALUES (:email, '감사관리자', 'ADMIN', 'ACTIVE')")
                    .setParameter("email", EMAIL).executeUpdate();
            em.createNativeQuery("INSERT INTO articles (source_type, title, content, status) "
                            + "VALUES ('SCHOOL', :title, '내용', 'PENDING_REVIEW')")
                    .setParameter("title", TITLE).executeUpdate();
        });
        adminId = scalar("SELECT id FROM users WHERE email = '" + EMAIL + "'");
        articleId = scalar("SELECT id FROM articles WHERE title = '" + TITLE + "'");
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM articles WHERE title = :title")
                    .setParameter("title", TITLE).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE email = :email")
                    .setParameter("email", EMAIL).executeUpdate();
        });
    }

    @Test
    @DisplayName("★ 관리자가 상태를 바꾸면 이력에 '누가·왜·언제' 가 전부 남는다")
    void auditTrailCarriesActorMemoAndTime() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/articles/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"article_ids": [%d], "status": "READY_TO_PUBLISH", "memo": "검수 완료"}
                                """.formatted(articleId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/articles/{id}/status-logs", articleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].to_status").value("READY_TO_PUBLISH"))
                .andExpect(jsonPath("$.data[0].memo").value("검수 완료"))
                .andExpect(jsonPath("$.data[0].changed_by").value(adminId))
                .andExpect(jsonPath("$.data[0].changed_by_name").value("감사관리자"))
                .andExpect(jsonPath("$.data[0].created_at").isNotEmpty());
    }

    @Test
    @DisplayName("일반 사용자는 관리자 API 에 접근할 수 없다")
    void nonAdminIsForbidden() throws Exception {
        String userToken = "Bearer " + jwtTokenProvider.createAccessToken(adminId, UserRole.USER);

        mockMvc.perform(get("/api/v1/admin/articles").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(adminId, UserRole.ADMIN);
    }

    private Long scalar(String sql) {
        return transactionTemplate.execute(status ->
                ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }
}
