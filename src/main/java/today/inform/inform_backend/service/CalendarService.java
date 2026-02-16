package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.dto.SchoolArticleResponse;
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
    private final SchoolArticleService schoolArticleService;

    @Transactional(readOnly = true)
    public List<SchoolArticleResponse> getMonthlyNotices(Integer year, Integer month, List<Integer> categoryIds, Boolean isMyOnly, Integer userId) {
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

        // 4. 벤더 정보 일괄 조회
        List<SchoolArticleVendor> savs = schoolArticleVendorRepository.findAllByArticleIn(articles);
        Map<Integer, List<SchoolArticleVendor>> vendorMap = savs.stream()
                .collect(Collectors.groupingBy(sav -> sav.getArticle().getArticleId()));

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

        // 7. 응답 변환 (SchoolArticleService의 로직 재사용)
        LocalDate today = LocalDate.now();
        return articles.stream()
                .map(article -> schoolArticleService.convertToResponse(
                        article, 
                        vendorMap.get(article.getArticleId()), 
                        today, 
                        finalBookmarkedIds.contains(article.getArticleId()), 
                        bookmarkCountMap.getOrDefault(article.getArticleId(), 0L)
                ))
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
        if ((categoryIds == null || categoryIds.isEmpty()) && !Boolean.TRUE.equals(isMyOnly)) {
            return List.of(1);
        }
        return categoryIds;
    }
}
