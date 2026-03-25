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
import today.inform.inform_backend.service.SchoolArticleService;
import today.inform.inform_backend.dto.AdminUnifiedDetailResponse;
import today.inform.inform_backend.dto.AdminUnifiedUpdateRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import today.inform.inform_backend.entity.SchoolArticleSandbox;
import today.inform.inform_backend.dto.SandboxArticleResponse;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
class AdminControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SchoolArticleSandboxService sandboxService;

    @MockitoBean
    private SchoolArticleService schoolArticleService;

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
    @DisplayName("샌드박스 목록 조회 API 테스트 (페이징)")
    @WithMockUser(roles = "ADMIN")
    void getSandboxArticlesTest() throws Exception {
        // given
        Page<SchoolArticleSandbox> page = new PageImpl<>(List.of());
        given(sandboxService.getArticlesByStatus(eq(AdminStatus.INSPECTED_YET), any(PageRequest.class))).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/sandbox/articles")
                        .param("status", "INSPECTED_YET")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sandbox_articles").isArray())
                .andExpect(jsonPath("$.data.page_info").exists())
                .andExpect(jsonPath("$.data.page_info.current_page").value(1));
    }

    @Test
    @DisplayName("[통합] sandbox 게시글 상세 조회 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void getUnifiedSandboxDetailTest() throws Exception {
        // given
        Integer id = 1;
        AdminUnifiedDetailResponse response = AdminUnifiedDetailResponse.builder()
                .source("sandbox")
                .id(id)
                .title("테스트 제목")
                .content("테스트 내용")
                .adminStatus("INSPECTED_YET")
                .build();

        given(sandboxService.getAdminSandboxDetail(id)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/articles/sandbox/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("sandbox"))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.title").value("테스트 제목"));
    }

    @Test
    @DisplayName("[통합] service 게시글 상세 조회 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void getUnifiedServiceDetailTest() throws Exception {
        // given
        Integer id = 101;
        AdminUnifiedDetailResponse response = AdminUnifiedDetailResponse.builder()
                .source("service")
                .id(id)
                .title("서비스 제목")
                .content("서비스 내용")
                .build();

        given(schoolArticleService.getAdminArticleDetail(id)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/articles/service/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("service"))
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("[통합] sandbox 게시글 수정 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void updateUnifiedSandboxTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/articles/sandbox/1")
                        .content("{\"title\":\"수정된 제목\", \"content\":\"수정된 내용\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sandboxService, times(1)).updateArticle(eq(1), any(AdminUnifiedUpdateRequest.class));
    }

    @Test
    @DisplayName("[통합] service 게시글 수정 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void updateUnifiedServiceTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/articles/service/101")
                        .content("{\"title\":\"수정된 직접 제목\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(schoolArticleService, times(1)).updateArticleDirectly(eq(101), any(AdminUnifiedUpdateRequest.class));
    }

    @Test
    @DisplayName("샌드박스 다중 상태 변경 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void updateSandboxStatusesTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/sandbox/articles/status")
                        .param("ids", "1,2,3")
                        .param("status", "GARBAGE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sandboxService, times(1)).updateStatuses(eq(List.of(1, 2, 3)), eq(AdminStatus.GARBAGE));
    }

    @Test
    @DisplayName("샌드박스 다중 실서비스 반영(Deploy) 테스트")
    @WithMockUser(roles = "ADMIN")
    void deploySandboxArticlesTest() throws Exception {
        // given
        given(sandboxService.deployArticles(List.of(1, 2))).willReturn(List.of(101, 102));

        // when & then
        mockMvc.perform(post("/api/v1/admin/sandbox/articles/deploy")
                        .param("ids", "1,2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value(101));
    }

    @Test
    @DisplayName("샌드박스 다중 삭제 테스트")
    @WithMockUser(roles = "ADMIN")
    void deleteSandboxArticlesTest() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/admin/sandbox/articles/delete")
                        .param("ids", "1,2,3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sandboxService, times(1)).deleteArticles(List.of(1, 2, 3));
    }

    @Test
    @DisplayName("샌드박스 휴지통 복구 테스트")
    @WithMockUser(roles = "ADMIN")
    void restoreSandboxArticlesTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/sandbox/articles/restore")
                        .param("ids", "1,2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sandboxService, times(1)).restoreArticles(List.of(1, 2));
    }

    @Test
    @DisplayName("서비스 게시글 직접 등록 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void createArticleDirectlyTest() throws Exception {
        // given
        given(schoolArticleService.createArticleDirectly(any())).willReturn(101);

        // when & then
        mockMvc.perform(post("/api/v1/admin/articles")
                        .content("{\"title\":\"직접 제목\", \"content\":\"직접 내용\", \"category_id\":1, \"start_date\":\"2026-03-18\", \"vendors\":[{\"vendor_id\":1}]}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(101));
    }

    @Test
    @DisplayName("서비스 게시글 ID 중복 확인 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void checkArticleIdExistsTest() throws Exception {
        // given
        given(schoolArticleService.checkArticleIdExists(999)).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/v1/admin/articles/check-id")
                        .param("article_id", "999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }
}
