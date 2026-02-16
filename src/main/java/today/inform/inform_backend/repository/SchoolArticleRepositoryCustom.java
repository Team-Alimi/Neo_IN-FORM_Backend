package today.inform.inform_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;
import java.util.List;

public interface SchoolArticleRepositoryCustom {
    Page<SchoolArticle> findAllWithFiltersAndSorting(
            List<Integer> categoryIds,
            String keyword,
            LocalDate today,
            LocalDate upcomingLimit,
            LocalDate endingSoonLimit,
            Pageable pageable
    );

    Page<SchoolArticle> findAllByIdsWithFiltersAndSorting(
            List<Integer> articleIds,
            List<Integer> categoryIds,
            String keyword,
            LocalDate today,
            LocalDate upcomingLimit,
            LocalDate endingSoonLimit,
            Pageable pageable
    );

    List<SchoolArticle> findHotArticles(LocalDate today, int limit);

    List<SchoolArticle> findCalendarArticles(
            List<String> categoryNames,
            Integer userId,
            LocalDate viewStart,
            LocalDate viewEnd
    );

    Page<SchoolArticle> findDailyCalendarArticles(
            LocalDate selectedDate,
            List<String> categoryNames,
            Integer userId,
            Pageable pageable
    );
}
