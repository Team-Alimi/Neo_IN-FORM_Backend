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

    private static final Map<String, String> CATEGORY_MAP = Map.of(
            "CONTEST", "대회•공모전",
            "LECTURE", "특강",
            "SCHOLAR", "장학",
            "ACTIVITY", "대내외 활동"
    );

    @Transactional(readOnly = true)
    public List<CalendarNoticeResponse> getMonthlyNotices(LocalDate viewStart, LocalDate viewEnd, List<String> categories, Integer userId) {
        if (viewStart.isAfter(viewEnd)) {
            throw new IllegalArgumentException("view_start cannot be after view_end");
        }

        List<String> mappedCategories = mapCategories(categories);
        List<SchoolArticle> articles = schoolArticleRepository.findCalendarArticles(mappedCategories, userId, viewStart, viewEnd);

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
    public CalendarDailyListResponse getDailyNotices(LocalDate selectedDate, List<String> categories, Integer page, Integer userId) {
        Pageable pageable = PageRequest.of(page - 1, 5); // 5개 고정
        List<String> mappedCategories = mapCategories(categories);

        Page<SchoolArticle> articlePage = schoolArticleRepository.findDailyCalendarArticles(selectedDate, mappedCategories, userId, pageable);

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

    private List<String> mapCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of("대회•공모전");
        }
        if (categories.contains("MY")) {
            return List.of("MY");
        }
        return categories.stream()
                .map(c -> CATEGORY_MAP.getOrDefault(c, c))
                .collect(Collectors.toList());
    }

    private String determineStatus(SchoolArticle article, LocalDate today) {
        if (article.getDueDate() != null && article.getDueDate().isBefore(today)) return "CLOSED";
        if (article.getStartDate() != null && article.getStartDate().isAfter(today)) return "UPCOMING";
        if (article.getDueDate() != null && !article.getDueDate().isAfter(today.plusDays(5))) return "ENDING_SOON";
        return "OPEN";
    }
}
