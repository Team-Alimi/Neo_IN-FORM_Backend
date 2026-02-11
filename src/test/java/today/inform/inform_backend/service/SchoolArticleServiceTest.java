package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.dto.SchoolArticleResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.entity.VendorType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SchoolArticleServiceTest {

    @Mock private SchoolArticleRepository schoolArticleRepository;
    @Mock private SchoolArticleVendorRepository schoolArticleVendorRepository;
    @Mock private AttachmentRepository attachmentRepository;

    @InjectMocks private SchoolArticleService schoolArticleService;

    @Test
    @DisplayName("학교 공지사항 상세 정보를 성공적으로 조회한다.")
    void getSchoolArticleDetail_Success() {
        // given
        Integer articleId = 105;
        LocalDate today = LocalDate.now();
        SchoolArticle article = SchoolArticle.builder()
                .articleId(articleId)
                .title("테스트 공지")
                .content("본문 내용")
                .startDate(today.plusDays(1))
                .dueDate(today.plusDays(5))
                .build();

        given(schoolArticleRepository.findById(articleId)).willReturn(Optional.of(article));
        given(schoolArticleVendorRepository.findAllByArticle(article)).willReturn(List.of());
        given(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL))
                .willReturn(List.of());

        // when
        SchoolArticleDetailResponse result = schoolArticleService.getSchoolArticleDetail(articleId);

        // then
        assertThat(result.getArticle_id()).isEqualTo(articleId);
        assertThat(result.getTitle()).isEqualTo("테스트 공지");
        assertThat(result.getStatus()).isEqualTo("UPCOMING");
    }

    @Test
    @DisplayName("날짜에 따라 공지사항의 상태(status)가 올바르게 판별된다.")
    void determineStatus_Test() {
        // given
        LocalDate today = LocalDate.now();
        
        // 1. OPEN: 오늘이 시작과 마감 사이
        SchoolArticle openArticle = SchoolArticle.builder()
                .articleId(1)
                .startDate(today.minusDays(1))
                .dueDate(today.plusDays(10))
                .build();

        // 2. UPCOMING: 시작일이 오늘부터 5일 이내 미래
        SchoolArticle upcomingArticle = SchoolArticle.builder()
                .articleId(2)
                .startDate(today.plusDays(3))
                .dueDate(today.plusDays(10))
                .build();

        // 3. ENDING_SOON: 마감일이 오늘부터 5일 이내 남음
        SchoolArticle endingSoonArticle = SchoolArticle.builder()
                .articleId(3)
                .startDate(today.minusDays(5))
                .dueDate(today.plusDays(2))
                .build();

        // 4. CLOSED: 마감이 지남
        SchoolArticle closedArticle = SchoolArticle.builder()
                .articleId(4)
                .startDate(today.minusDays(10))
                .dueDate(today.minusDays(1))
                .build();

        Page<SchoolArticle> page = new PageImpl<>(List.of(openArticle, upcomingArticle, endingSoonArticle, closedArticle));
        given(schoolArticleRepository.findAllWithFiltersAndSorting(any(), any(), any(), any(), any(), any())).willReturn(page);
        given(schoolArticleVendorRepository.findAllByArticleIn(any())).willReturn(List.of());

        // when
        SchoolArticleListResponse response = schoolArticleService.getSchoolArticles(1, 10, null, null);

        // then
        List<SchoolArticleResponse> articles = response.getSchool_articles();
        assertThat(articles.get(0).getStatus()).isEqualTo("OPEN");
        assertThat(articles.get(1).getStatus()).isEqualTo("UPCOMING");
        assertThat(articles.get(2).getStatus()).isEqualTo("ENDING_SOON");
        assertThat(articles.get(3).getStatus()).isEqualTo("CLOSED");
    }
}
