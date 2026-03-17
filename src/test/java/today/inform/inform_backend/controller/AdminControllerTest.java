package today.inform.inform_backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.service.SchoolArticleSandboxService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SchoolArticleSandboxService sandboxService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("샌드박스 통계 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void getSandboxCountsTest() throws Exception {
        // given
        Map<String, Long> counts = new HashMap<>();
        counts.put("inspected_yet", 10L);
        counts.put("reflection_waiting", 5L);
        counts.put("suspected_duplicate", 2L);
        counts.put("garbage", 0L);
        
        given(sandboxService.getSandboxCounts()).willReturn(counts);

        // when & then
        mockMvc.perform(get("/api/v1/admin/sandbox/counts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspected_yet").value(10))
                .andExpect(jsonPath("$.data.reflection_waiting").value(5))
                .andExpect(jsonPath("$.data.suspected_duplicate").value(2))
                .andExpect(jsonPath("$.data.garbage").value(0));
    }

    @Test
    @DisplayName("샌드박스 목록 조회 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void getSandboxArticlesTest() throws Exception {
        // given
        given(sandboxService.getArticlesByStatus(AdminStatus.INSPECTED_YET)).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/admin/sandbox/articles")
                        .param("status", "INSPECTED_YET")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.articles").isArray());
    }
}
