package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.dto.CalendarNoticeResponse;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final SchoolArticleRepository schoolArticleRepository;
    private final SchoolArticleVendorRepository schoolArticleVendorRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CalendarNoticeResponse> getMonthlyNotices(Integer year, Integer month, List<Integer> categoryIds, Boolean isMyOnly, Integer userId) {
        // 1. 기본값 설정
        List<Integer> effectiveCategoryIds = getEffectiveCategoryIds(categoryIds, isMyOnly);

        // 2. 해당 월의 시작일과 종료일 계산
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        // 3. 게시글 조회
        List<SchoolArticle> articles = schoolArticleRepository.findCalendarArticles(effectiveCategoryIds, isMyOnly, userId, startOfMonth, endOfMonth);

        if (articles.isEmpty()) {
            return List.of();
        }

        List<Integer> articleIds = articles.stream().map(SchoolArticle::getArticleId).collect(Collectors.toList());

        // 4. 벤더 정보 일괄 조회 (N+1 방지)
        List<SchoolArticleVendor> savs = schoolArticleVendorRepository.findAllByArticleIn(articles);
        Map<Integer, List<VendorListResponse>> vendorMap = savs.stream()
                .collect(Collectors.groupingBy(
                        sav -> sav.getArticle().getArticleId(),
                        Collectors.mapping(sav -> VendorListResponse.builder()
                                .vendorId(sav.getVendor().getVendorId())
                                .vendorName(sav.getVendor().getVendorName())
                                .vendorInitial(sav.getVendor().getVendorInitial())
                                .vendorType(sav.getVendor().getVendorType().name())
                                .build(), Collectors.toList())
                ));

        // 5. 북마크 여부 일괄 확인
        java.util.Set<Integer> bookmarkedIds = new java.util.HashSet<>();
        if (userId != null) {
            today.inform.inform_backend.entity.User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                bookmarkedIds = bookmarkRepository.findAllByUserAndArticleTypeAndArticleIdIn(user, today.inform.inform_backend.entity.VendorType.SCHOOL, articleIds)
                        .stream()
                        .map(today.inform.inform_backend.entity.Bookmark::getArticleId)
                        .collect(Collectors.toSet());
            }
        }
        final java.util.Set<Integer> finalBookmarkedIds = bookmarkedIds;

        // 6. 북마크 개수 일괄 조회
        Map<Integer, Long> bookmarkCountMap = getBookmarkCountMap(articleIds);

        // 7. 응답 변환
        LocalDate today = LocalDate.now();
        return articles.stream()
                .map(article -> CalendarNoticeResponse.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .startDate(article.getStartDate())
                        .dueDate(article.getDueDate())
                        .categoryName(article.getCategory() != null ? article.getCategory().getCategoryName() : null)
                        .status(determineStatus(article, today))
                        .isBookmarked(finalBookmarkedIds.contains(article.getArticleId()))
                        .bookmarkCount(bookmarkCountMap.getOrDefault(article.getArticleId(), 0L))
                        .vendors(vendorMap.getOrDefault(article.getArticleId(), List.of()))
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Integer, Long> getBookmarkCountMap(List<Integer> articleIds) {
        return bookmarkRepository.countByArticleIdsAndArticleType(articleIds, today.inform.inform_backend.entity.VendorType.SCHOOL)
                .stream()
                .collect(Collectors.toMap(
                        obj -> (Integer) obj[0],
                        obj -> (Long) obj[1]
                ));
    }

    private List<Integer> getEffectiveCategoryIds(List<Integer> categoryIds, Boolean isMyOnly) {
        // 카테고리 필터가 없고, '내 일정만 보기'도 체크 안 된 경우에만 ID 1번(대회•공모전)을 기본값으로 사용
        if ((categoryIds == null || categoryIds.isEmpty()) && !Boolean.TRUE.equals(isMyOnly)) {
            return List.of(1);
        }
        return categoryIds;
    }

    private String determineStatus(SchoolArticle article, LocalDate today) {
        if (article.getDueDate() != null && article.getDueDate().isBefore(today)) return "CLOSED";
        if (article.getStartDate() != null && article.getStartDate().isAfter(today)) return "UPCOMING";
        if (article.getDueDate() != null && !article.getDueDate().isAfter(today.plusDays(5))) return "ENDING_SOON";
        return "OPEN";
    }
}
