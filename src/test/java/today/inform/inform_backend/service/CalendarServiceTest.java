package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.dto.SchoolArticleResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @InjectMocks
    private CalendarService calendarService;

    @Mock
    private SchoolArticleRepository schoolArticleRepository;

    @Mock
    private SchoolArticleVendorRepository schoolArticleVendorRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolArticleService schoolArticleService;

    @Test
    @DisplayName("월간 일정 조회 시 카테고리 필터가 없으면 기본값(ID: 1)이 적용된다.")
    void getMonthlyNotices_DefaultCategory() {
        // given
        given(schoolArticleRepository.findCalendarArticles(eq(List.of(1)), any(), any(), any(), any()))
                .willReturn(List.of());

        // when
        List<SchoolArticleResponse> response = calendarService.getMonthlyNotices(2026, 2, null, null, null);

        // then
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("isMyOnly가 true이면 카테고리 ID가 전달된 대로 유지되며 내 일정 필터가 작동한다.")
    void getMonthlyNotices_MyFilter() {
        // given
        List<Integer> categoryIds = List.of(1, 2);
        given(schoolArticleRepository.findCalendarArticles(eq(categoryIds), eq(true), any(), any(), any()))
                .willReturn(List.of());

        // when
        calendarService.getMonthlyNotices(2026, 2, categoryIds, true, 1);

        // then
    }

    @Test
    @DisplayName("일정 조회 결과가 있으면 SchoolArticleService를 통해 응답이 변환된다.")
    void getMonthlyNotices_Success() {
        // given
        SchoolArticle article = SchoolArticle.builder()
                .articleId(100)
                .title("테스트")
                .startDate(LocalDate.of(2026, 2, 1))
                .dueDate(LocalDate.of(2026, 2, 28))
                .build();

        given(schoolArticleRepository.findCalendarArticles(any(), any(), any(), any(), any()))
                .willReturn(List.of(article));
        given(schoolArticleVendorRepository.findAllByArticleIn(any())).willReturn(List.of());
        given(bookmarkRepository.countByArticleIdsAndArticleType(any(), any())).willReturn(List.of());
        
        given(schoolArticleService.convertToResponse(any(), any(), any(), anyBoolean(), anyLong()))
                .willReturn(SchoolArticleResponse.builder().articleId(100).build());

        // when
        List<SchoolArticleResponse> response = calendarService.getMonthlyNotices(2026, 2, null, null, null);

        // then
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getArticleId()).isEqualTo(100);
    }
}
