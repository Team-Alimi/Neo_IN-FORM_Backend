package today.inform.inform_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;

public interface SchoolArticleRepositoryCustom {
    Page<SchoolArticle> findAllWithFiltersAndSorting(
            Integer categoryId,
            String keyword,
            LocalDate today,
            LocalDate upcomingLimit,
            LocalDate endingSoonLimit,
            Pageable pageable
    );
}
