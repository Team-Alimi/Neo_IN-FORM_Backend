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
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.service.SchoolArticleService;
import today.inform.inform_backend.dto.AdminUnifiedDetailResponse;
import today.inform.inform_backend.dto.AdminUnifiedUpdateRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
    private SchoolArticleService schoolArticleService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("미배포 게시글 통계 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void getUnpublishedCountsTest() throws Exception {
        // given
        Map<String, Long> counts = new HashMap<>();
        counts.put("inspected_yet", 10L);
        counts.put("reflection_waiting", 5L);
        counts.put("suspected_duplicate", 2L);
        counts.put("garbage", 0L);

        given(schoolArticleService.getUnpublishedCounts()).willReturn(counts);

        // when & then
        mockMvc.perform(get("/api/v1/admin/articles/counts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspected_yet").value(10))
                .andExpect(jsonPath("$.data.reflection_waiting").value(5))
                .andExpect(jsonPath("$.data.suspected_duplicate").value(2))
                .andExpect(jsonPath("$.data.garbage").value(0));
    }

    @Test
    @DisplayName("미배포 게시글 목록 조회 API 테스트 (페이징)")
    @WithMockUser(roles = "ADMIN")
    void getUnpublishedArticlesTest() throws Exception {
        // given
        Page<SchoolArticle> page = new PageImpl<>(List.of());
        given(schoolArticleService.getUnpublishedArticlesByStatus(eq(AdminStatus.INSPECTED_YET), any(PageRequest.class))).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/articles")
                        .param("status", "INSPECTED_YET")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.school_articles").isArray())
                .andExpect(jsonPath("$.data.page_info").exists())
                .andExpect(jsonPath("$.data.page_info.current_page").value(1));
    }

    @Test
    @DisplayName("게시글 상세 조회 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void getArticleDetailTest() throws Exception {
        // given
        Integer id = 1;
        AdminUnifiedDetailResponse response = AdminUnifiedDetailResponse.builder()
                .id(id)
                .title("테스트 제목")
                .content("테스트 내용")
                .isPublished(false)
                .adminStatus("INSPECTED_YET")
                .build();

        given(schoolArticleService.getAdminArticleDetail(id)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/articles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.title").value("테스트 제목"));
    }

    @Test
    @DisplayName("게시글 수정 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void updateArticleTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/articles/1")
                        .content("{\"title\":\"수정된 제목\", \"content\":\"수정된 내용\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(schoolArticleService, times(1)).updateArticle(eq(1), any(AdminUnifiedUpdateRequest.class));
    }

    @Test
    @DisplayName("다중 상태 변경 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void updateStatusesTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/articles/status")
                        .param("ids", "1,2,3")
                        .param("status", "GARBAGE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(schoolArticleService, times(1)).updateStatuses(eq(List.of(1, 2, 3)), eq(AdminStatus.GARBAGE));
    }

    @Test
    @DisplayName("서비스 배포(Deploy) API 테스트")
    @WithMockUser(roles = "ADMIN")
    void deployArticlesTest() throws Exception {
        // given
        given(schoolArticleService.deployArticles(List.of(1, 2))).willReturn(List.of(1, 2));

        // when & then
        mockMvc.perform(post("/api/v1/admin/articles/deploy")
                        .param("ids", "1,2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value(1));
    }

    @Test
    @DisplayName("게시글 영구 삭제 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void deleteArticlesTest() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/admin/articles/delete")
                        .param("ids", "1,2,3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(schoolArticleService, times(1)).deleteArticles(List.of(1, 2, 3));
    }

    @Test
    @DisplayName("휴지통 게시글 복구 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void restoreArticlesTest() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/articles/restore")
                        .param("ids", "1,2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(schoolArticleService, times(1)).restoreArticles(List.of(1, 2));
    }

    @Test
    @DisplayName("서비스 게시글 직접 등록 API 테스트")
    @WithMockUser(roles = "ADMIN")
    void createArticleDirectlyTest() throws Exception {
        // given
        given(schoolArticleService.createArticleDirectly(any())).willReturn(101);

        // when & then
        mockMvc.perform(post("/api/v1/admin/articles/create")
                        .content("{\"title\":\"직접 제목\", \"content\":\"직접 내용\", \"category_id\":1, \"start_date\":\"2026-03-18\", \"vendors\":[{\"vendor_id\":1}]}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(101));
    }

    @Test
    @DisplayName("게시글 ID 중복 확인 API 테스트")
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
