package today.inform.inform_backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.service.SchoolArticleService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SchoolArticleControllerTest {

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
    @DisplayName("공지사항 상세 조회 시 ApiResponse 구조로 응답한다.")
    @WithMockUser // 인증 건너뛰기
    void getSchoolArticleDetail_ShouldReturnApiResponse() throws Exception {
        // given
        Integer articleId = 105;
        SchoolArticleDetailResponse response = SchoolArticleDetailResponse.builder()
                .article_id(articleId)
                .title("테스트 제목")
                .status("OPEN")
                .vendors(List.of())
                .attachments(List.of())
                .build();

        given(schoolArticleService.getSchoolArticleDetail(articleId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/school_articles/" + articleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.article_id").value(articleId))
                .andExpect(jsonPath("$.data.title").value("테스트 제목"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }
}
