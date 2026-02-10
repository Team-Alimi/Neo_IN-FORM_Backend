package today.inform.inform_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;

public interface SchoolArticleRepository extends JpaRepository<SchoolArticle, Integer> {

    @Query(value = "SELECT sa FROM SchoolArticle sa " +
            "LEFT JOIN FETCH sa.category " +
            "WHERE (:categoryId IS NULL OR sa.category.categoryId = :categoryId) " +
            "AND (:keyword IS NULL OR sa.title LIKE %:keyword%) " +
            "ORDER BY " +
            "CASE " +
            "  WHEN sa.startDate <= :today AND sa.dueDate >= :today THEN 1 " + // OPEN
            "  WHEN sa.startDate > :today AND sa.startDate <= :upcomingLimit THEN 2 " + // UPCOMING
            "  WHEN sa.dueDate >= :today AND sa.dueDate <= :endingSoonLimit THEN 3 " + // ENDING_SOON
            "  WHEN sa.dueDate < :today THEN 5 " + // CLOSED
            "  ELSE 4 END ASC, " + // NORMAL
            "sa.createdAt DESC",
            countQuery = "SELECT COUNT(sa) FROM SchoolArticle sa WHERE (:categoryId IS NULL OR sa.category.categoryId = :categoryId) AND (:keyword IS NULL OR sa.title LIKE %:keyword%)")
    Page<SchoolArticle> findAllWithFiltersAndSorting(
            @Param("categoryId") Integer categoryId,
            @Param("keyword") String keyword,
            @Param("today") LocalDate today,
            @Param("upcomingLimit") LocalDate upcomingLimit,
            @Param("endingSoonLimit") LocalDate endingSoonLimit,
            Pageable pageable
    );
}
