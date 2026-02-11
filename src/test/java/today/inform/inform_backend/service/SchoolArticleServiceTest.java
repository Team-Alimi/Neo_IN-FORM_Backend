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
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.repository.UserRepository;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.entity.VendorType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SchoolArticleServiceTest {

    @Mock private SchoolArticleRepository schoolArticleRepository;
    @Mock private SchoolArticleVendorRepository schoolArticleVendorRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private SchoolArticleService schoolArticleService;

    @Test
    @DisplayName("학교 공지사항 상세 정보를 성공적으로 조회한다.")
    void getSchoolArticleDetail_Success() {
        // given
        Integer articleId = 105;
        Integer userId = 1;
        LocalDate todayDate = LocalDate.now();
        SchoolArticle article = SchoolArticle.builder()
                .articleId(articleId)
                .title("테스트 공지")
                .content("본문 내용")
                .startDate(todayDate.plusDays(1))
                .dueDate(todayDate.plusDays(5))
                .build();

        User user = User.builder()
                .userId(userId)
                .build();

        given(schoolArticleRepository.findById(articleId)).willReturn(Optional.of(article));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(bookmarkRepository.existsByUserAndArticleTypeAndArticleId(any(), any(), any())).willReturn(false);
        given(schoolArticleVendorRepository.findAllByArticle(article)).willReturn(List.of());
        given(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL))
                .willReturn(List.of());

        // when
        SchoolArticleDetailResponse result = schoolArticleService.getSchoolArticleDetail(articleId, userId);

        // then
        assertThat(result.getArticle_id()).isEqualTo(articleId);
        assertThat(result.getTitle()).isEqualTo("테스트 공지");
        assertThat(result.getStatus()).isEqualTo("UPCOMING");
    }

    @Test
    @DisplayName("인기 게시물 목록을 성공적으로 조회한다.")
    void getHotSchoolArticles_Success() {
        // given
        Integer userId = 1;
        SchoolArticle article = SchoolArticle.builder().articleId(10).title("인기글").build();
        given(schoolArticleRepository.findHotArticles(any(), eq(10))).willReturn(List.of(article));
        
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(bookmarkRepository.findAllByUserAndArticleTypeAndArticleIdIn(any(), any(), any())).willReturn(List.of());
        given(schoolArticleVendorRepository.findAllByArticleIn(any())).willReturn(List.of());

        // when
        List<SchoolArticleResponse> result = schoolArticleService.getHotSchoolArticles(userId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("인기글");
    }

    @Test
    @DisplayName("날짜에 따라 공지사항의 상태(status)가 올바르게 판별된다.")
    void determineStatus_Test() {
        // given
        Integer userId = 1;
        LocalDate todayDate = LocalDate.now();
        
        // 1. OPEN: 오늘이 시작과 마감 사이
        SchoolArticle openArticle = SchoolArticle.builder()
                .articleId(1)
                .startDate(todayDate.minusDays(1))
                .dueDate(todayDate.plusDays(10))
                .build();

        Page<SchoolArticle> page = new PageImpl<>(List.of(openArticle));
        given(schoolArticleRepository.findAllWithFiltersAndSorting(any(), any(), any(), any(), any(), any())).willReturn(page);
        given(schoolArticleVendorRepository.findAllByArticleIn(any())).willReturn(List.of());
        
        // 북마크 Mock
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(bookmarkRepository.findAllByUserAndArticleTypeAndArticleIdIn(any(), any(), any())).willReturn(List.of());

        // when
        SchoolArticleListResponse response = schoolArticleService.getSchoolArticles(1, 10, null, null, userId);

        // then
        List<SchoolArticleResponse> articles = response.getSchool_articles();
        assertThat(articles.get(0).getStatus()).isEqualTo("OPEN");
        assertThat(articles.get(0).getIs_bookmarked()).isFalse();
    }
}