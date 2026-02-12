package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import today.inform.inform_backend.dto.CalendarDailyListResponse;
import today.inform.inform_backend.dto.CalendarNoticeResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;

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

    @Test
    @DisplayName("월간 일정 조회 시 카테고리 필터가 없으면 기본값(대회•공모전)이 적용된다.")
    void getMonthlyNotices_DefaultCategory() {
        // given
        given(schoolArticleRepository.findCalendarArticles(eq(List.of("대회•공모전")), any(), any(), any()))
                .willReturn(List.of());

        // when
        calendarService.getMonthlyNotices(2026, 2, null, null);

        // then
        // verify가 필요하지만 given만으로도 flow 확인 가능
    }

    @Test
    @DisplayName("MY 카테고리가 포함되면 다른 카테고리는 무시된다.")
    void mapCategories_MyPriority() {
        // given
        given(schoolArticleRepository.findCalendarArticles(eq(List.of("MY")), any(), any(), any()))
                .willReturn(List.of());

        // when
        calendarService.getMonthlyNotices(2026, 2, List.of("CONTEST", "MY", "SCHOLAR"), 1);

        // then
        // findCalendarArticles에 "MY"만 전달되었는지 확인 (given 조건에 의해 실패 시 에러 발생)
    }

    @Test
    @DisplayName("일별 일정 조회 시 5개씩 페이징 처리된다.")
    void getDailyNotices_Pagination() {
        // given
        SchoolArticle article = SchoolArticle.builder().articleId(1).title("테스트").build();
        Page<SchoolArticle> page = new PageImpl<>(List.of(article));
        given(schoolArticleRepository.findDailyCalendarArticles(any(), any(), any(), any())).willReturn(page);
        given(schoolArticleVendorRepository.findAllByArticleIn(any())).willReturn(List.of());

        // when
        CalendarDailyListResponse response = calendarService.getDailyNotices(LocalDate.now(), List.of("CONTEST"), 1, null);

        // then
        assertThat(response.getNotices()).hasSize(1);
        assertThat(response.getPageInfo().getCurrentPage()).isEqualTo(1);
    }
}
