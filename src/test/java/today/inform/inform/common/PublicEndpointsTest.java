package today.inform.inform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.support.IntegrationTest;

/**
 * 비로그인으로 열려 있는 네 경로 — CAL-01, COM-01, COM-02, USER-04.
 *
 * <p><b>여기서 확인하는 것은 "열려 있는가" 와 "닫아야 할 것이 새지 않는가" 입니다.</b>
 * {@code SecurityConfig} 의 매처는 서비스 테스트로는 검증되지 않습니다 —
 * 서비스를 직접 부르면 필터 체인을 타지 않기 때문입니다.
 * 매처 한 줄이 잘못되면 온보딩 첫 화면이 통째로 401 이 되거나,
 * 반대로 로그인 전용 목록이 열립니다.
 *
 * <p>{@code @Transactional} 이 없습니다 — MockMvc 요청은 별도 트랜잭션에서 돕니다.
 */
@AutoConfigureMockMvc
class PublicEndpointsTest extends IntegrationTest {

    private static final String HIDDEN_VENDOR = "HIDDENVENDOR";
    /**
     * 정렬을 구분하는 대조군 넷. 세 가지 후보 동작이 전부 다른 순서를 냅니다.
     * <ul>
     *   <li>한국어 순서(정답) — 정보통신 · 컴퓨터 · 하하동아리 · 학생지원</li>
     *   <li>유형 먼저(관리자용) — 하하동아리(CLUB)가 맨 앞</li>
     *   <li>DB collation({@code en_US.utf8}) — 한글 규칙이 없어 뒤죽박죽</li>
     * </ul>
     */
    private static final List<String> ORDER_FIXTURE = List.of(
            "SORT_JEONGBO", "SORT_KEOMPYUTEO", "SORT_HAHA", "SORT_HAKSAENG");
    private static final List<String> EXPECTED_ORDER = List.of(
            "정보통신테스트", "컴퓨터테스트", "하하동아리테스트", "학생지원테스트");
    private static final String HIDDEN_CATEGORY = "HIDDEN_CATEGORY";
    private static final String EMAIL = "public-endpoint@inha.ac.kr";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    private Long userId;

    @BeforeEach
    void setUp() {
        cleanUp();
        transactionTemplate.executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO vendors (name, initial, type, is_active) "
                            + "VALUES ('접힌학과', :i, 'SCHOOL', false)")
                    .setParameter("i", HIDDEN_VENDOR).executeUpdate();
            // ★ 활성 제공처가 하나도 없으면 GET /vendors 가 항상 빈 배열이라
            //   $.data[0] 을 보는 검사가 통째로 무의미해집니다(경로가 없으면 doesNotExist 는 그냥 통과).
            //   V5 는 vendors 를 의도적으로 시드하지 않고, 다른 테스트는 전부 @Transactional 이라 롤백됩니다.
            insertVendor(EXPECTED_ORDER.get(0), ORDER_FIXTURE.get(0), "SCHOOL");
            insertVendor(EXPECTED_ORDER.get(1), ORDER_FIXTURE.get(1), "SCHOOL");
            insertVendor(EXPECTED_ORDER.get(2), ORDER_FIXTURE.get(2), "CLUB");
            insertVendor(EXPECTED_ORDER.get(3), ORDER_FIXTURE.get(3), "SCHOOL");
            em.createNativeQuery("INSERT INTO categories (code, name, is_active, sort_order) "
                            + "VALUES (:c, '접힌 분류', false, 999)")
                    .setParameter("c", HIDDEN_CATEGORY).executeUpdate();
            em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                            + "VALUES (:e, '공개테스터', 'USER', 'ACTIVE')")
                    .setParameter("e", EMAIL).executeUpdate();
        });
        userId = transactionTemplate.execute(status ->
                ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :e")
                        .setParameter("e", EMAIL).getSingleResult()).longValue());
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            List<String> initials = new java.util.ArrayList<>(ORDER_FIXTURE);
            initials.add(HIDDEN_VENDOR);
            em.createNativeQuery("DELETE FROM vendors WHERE initial IN (:initials)")
                    .setParameter("initials", initials).executeUpdate();
            em.createNativeQuery("DELETE FROM categories WHERE code = :c")
                    .setParameter("c", HIDDEN_CATEGORY).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE email = :e")
                    .setParameter("e", EMAIL).executeUpdate();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 열려 있는가
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 토큰 없이 열린다 — 온보딩 첫 화면이 이 셋으로 그려진다")
    void guestCanReachPublicLists() throws Exception {
        mockMvc.perform(get("/api/v1/vendors")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/calendar/articles").param("year", "2026").param("month", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공지 목록은 여전히 로그인이 필요하다")
    void articleListStaysBehindLogin() throws Exception {
        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("★ 인기 공지는 비로그인도 볼 수 있다 — 홈이 캘린더 화면이고 그 상단에 그려진다")
    void popularIsOpenToGuests() throws Exception {
        mockMvc.perform(get("/api/v1/articles/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("★ 비로그인 인기 공지는 개인화 값이 false 다 — principal 이 null 이어도 터지지 않는다")
    void popularForGuestHasNoPersonalization() throws Exception {
        mockMvc.perform(get("/api/v1/articles/popular").param("limit", "5"))
                .andExpect(status().isOk())
                // 비어 있을 수 있으므로 개별 항목을 단정하지 않습니다.
                // 여기서 지키는 것은 "principal 이 null 인 경로가 500 이 되지 않는다" 입니다.
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("인기 목록에서 상세로 넘어가면 로그인 벽을 만난다")
    void popularDetailStillNeedsLogin() throws Exception {
        mockMvc.perform(get("/api/v1/articles/1"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 닫아야 할 것
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 비활성 제공처는 사용자 목록에 나오지 않는다 — 나오면 없어진 학과를 구독한다")
    void inactiveVendorIsHidden() throws Exception {
        mockMvc.perform(get("/api/v1/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '접힌학과')]").isEmpty());
    }

    @Test
    @DisplayName("★ 비활성 분류는 목록에 나오지 않는다 — 나오면 고를 수 있는 척하고 저장에서 400 이 난다")
    void inactiveCategoryIsHidden() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '접힌 분류')]").isEmpty());
    }

    @Test
    @DisplayName("★ 사용자 목록에는 크롤러 계약 키가 나가지 않는다")
    void internalContractKeysAreNotExposed() throws Exception {
        mockMvc.perform(get("/api/v1/vendors"))
                // ★ 먼저 배열이 비어 있지 않음을 못 박습니다. 비어 있으면 아래 $.data[0] 은
                //   경로 자체가 없어서 doesNotExist() 가 무조건 통과합니다 — 검사가 사라집니다.
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].initial").doesNotExist())
                .andExpect(jsonPath("$.data[0].is_active").doesNotExist());
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].code").doesNotExist());
    }

    @Test
    @DisplayName("★ 제공처 목록이 한국어 순서로 나온다 — 유형 먼저도, DB collation 순서도 아니다")
    void vendorListIsOrderedByKoreanName() throws Exception {
        List<String> names = vendorNames(get("/api/v1/vendors"));

        assertThat(names)
                .as("유형 먼저면 하하동아리(CLUB)가 맨 앞, DB collation(en_US)이면 한글이 뒤죽박죽입니다")
                .containsSubsequence(EXPECTED_ORDER.toArray(new String[0]));
    }

    @Test
    @DisplayName("유형 필터가 실제로 걸린다")
    void vendorTypeFilterApplies() throws Exception {
        List<String> clubs = vendorNames(get("/api/v1/vendors").param("type", "CLUB"));

        assertThat(clubs).contains("하하동아리테스트").doesNotContain("정보통신테스트");
    }

    @Test
    @DisplayName("★ 잘못된 월은 400 이다 — 500 이면 프론트가 재시도 대상으로 다룬다")
    void invalidMonthIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/calendar/articles").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("비로그인으로 '내 학과만' 을 요청하면 401")
    void guestCannotAskForMyMajor() throws Exception {
        mockMvc.perform(get("/api/v1/calendar/articles")
                        .param("year", "2026").param("month", "5")
                        .param("my_major_only", "true"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 응답 본문에서 제공처 이름만 순서대로 뽑습니다. 절대 위치가 아니라 상대 순서를 보기 위함입니다. */
    private List<String> vendorNames(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode data =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data");

        List<String> names = new java.util.ArrayList<>();
        data.forEach(node -> names.add(node.get("name").asText()));
        return names;
    }

    private void insertVendor(String name, String initial, String type) {
        em.createNativeQuery("INSERT INTO vendors (name, initial, type, is_active) "
                        + "VALUES (:n, :i, :t, true)")
                .setParameter("n", name).setParameter("i", initial).setParameter("t", type)
                .executeUpdate();
    }

    private boolean emailEnabled(Long id) {
        return transactionTemplate.execute(status ->
                (Boolean) em.createNativeQuery(
                                "SELECT email_notification_enabled FROM users WHERE id = :id")
                        .setParameter("id", id).getSingleResult());
    }
}
