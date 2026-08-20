package today.inform.inform.article;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import today.inform.inform.global.security.JwtTokenProvider;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.user.entity.UserRole;

/**
 * 잘못된 요청이 400 으로 나가는지 확인합니다.
 *
 * <p>여기서 500 이 나가면 단순히 상태 코드가 틀린 게 아닙니다 —
 * 프론트는 5xx 를 재시도 대상으로 다루므로, 고쳐질 리 없는 잘못된 요청을 계속 반복합니다.
 */
@AutoConfigureMockMvc
class ArticleRequestValidationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * {@code @WithMockUser} 를 쓰지 않습니다.
     *
     * <p>이 애플리케이션은 stateless 라 SecurityContext 를 저장소에서 복원하는 필터가
     * 요청 시작 시 컨텍스트를 비웁니다. 테스트가 미리 넣어 둔 인증이 그때 지워져 401 이 납니다.
     * 실제 토큰을 발급해 진짜 필터를 태우는 편이 검증 대상에도 더 가깝습니다.
     */
    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(1L, UserRole.USER);
    }

    @Test
    @DisplayName("경로 변수 타입이 안 맞으면 400 이다")
    void pathVariableTypeMismatch() throws Exception {
        mockMvc.perform(put("/api/v1/bookmarks/articles/abc").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("enum 파라미터 값이 안 맞으면 400 이다")
    void enumParameterMismatch() throws Exception {
        mockMvc.perform(delete("/api/v1/bookmarks").header(HttpHeaders.AUTHORIZATION, bearer()).param("source_type", "club"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("날짜 형식이 안 맞으면 400 이다")
    void dateParameterMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/articles").header(HttpHeaders.AUTHORIZATION, bearer()).param("starts_from", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }
}
