package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.dto.CalendarDailyListResponse;
import today.inform.inform_backend.dto.CalendarNoticeResponse;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final SchoolArticleRepository schoolArticleRepository;
    private final SchoolArticleVendorRepository schoolArticleVendorRepository;

    @Transactional(readOnly = true)
    public List<CalendarNoticeResponse> getMonthlyNotices(Integer year, Integer month, List<Integer> categoryIds, Boolean isMyOnly, Integer userId) {
        // 1. 해당 월의 시작일과 종료일 계산
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<SchoolArticle> articles = schoolArticleRepository.findCalendarArticles(categoryIds, isMyOnly, userId, startOfMonth, endOfMonth);

        return articles.stream()
                .map(article -> CalendarNoticeResponse.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .startDate(article.getStartDate())
                        .dueDate(article.getDueDate())
                        .categoryName(article.getCategory() != null ? article.getCategory().getCategoryName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CalendarDailyListResponse getDailyNotices(LocalDate selectedDate, List<Integer> categoryIds, Boolean isMyOnly, Integer page, Integer userId) {
        Pageable pageable = PageRequest.of(page - 1, 5); // 한 페이지 5개 고정

        Page<SchoolArticle> articlePage = schoolArticleRepository.findDailyCalendarArticles(selectedDate, categoryIds, isMyOnly, userId, pageable);

        if (articlePage.isEmpty()) {
            return CalendarDailyListResponse.builder()
                    .pageInfo(CalendarDailyListResponse.PageInfo.builder()
                            .currentPage(page)
                            .totalPages(0)
                            .totalArticles(0L)
                            .hasNext(false)
                            .build())
                    .notices(List.of())
                    .build();
        }

        List<SchoolArticleVendor> vendors = schoolArticleVendorRepository.findAllByArticleIn(articlePage.getContent());
        Map<Integer, List<VendorListResponse>> vendorMap = vendors.stream()
                .collect(Collectors.groupingBy(
                        (SchoolArticleVendor v) -> v.getArticle().getArticleId(),
                        Collectors.mapping((SchoolArticleVendor v) -> VendorListResponse.builder()
                                .vendorId(v.getVendor().getVendorId())
                                .vendorName(v.getVendor().getVendorName())
                                .vendorInitial(v.getVendor().getVendorInitial())
                                .vendorType(v.getVendor().getVendorType().name())
                                .build(), Collectors.toList())
                ));

        List<CalendarDailyListResponse.NoticeResponse> notices = articlePage.getContent().stream()
                .map(article -> CalendarDailyListResponse.NoticeResponse.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .status(determineStatus(article, LocalDate.now()))
                        .vendors(vendorMap.getOrDefault(article.getArticleId(), List.of()))
                        .startDate(article.getStartDate())
                        .categoryName(article.getCategory() != null ? article.getCategory().getCategoryName() : null)
                        .build())
                .collect(Collectors.toList());

        return CalendarDailyListResponse.builder()
                .pageInfo(CalendarDailyListResponse.PageInfo.builder()
                        .currentPage(page)
                        .totalPages(articlePage.getTotalPages())
                        .totalArticles(articlePage.getTotalElements())
                        .hasNext(articlePage.hasNext())
                        .build())
                .notices(notices)
                .build();
    }

    private String determineStatus(SchoolArticle article, LocalDate today) {
        if (article.getDueDate() != null && article.getDueDate().isBefore(today)) return "CLOSED";
        if (article.getStartDate() != null && article.getStartDate().isAfter(today)) return "UPCOMING";
        if (article.getDueDate() != null && !article.getDueDate().isAfter(today.plusDays(5))) return "ENDING_SOON";
        return "OPEN";
    }
}
