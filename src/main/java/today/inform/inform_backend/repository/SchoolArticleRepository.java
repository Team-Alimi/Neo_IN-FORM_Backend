package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;
import java.util.List;

public interface SchoolArticleRepository extends JpaRepository<SchoolArticle, Integer>, SchoolArticleRepositoryCustom {
    List<SchoolArticle> findAllByDueDate(LocalDate dueDate);

    @Modifying
    @Query(value = "INSERT INTO school_articles (article_id, title, content, category_id, start_date, due_date, created_at, updated_at) " +
                   "VALUES (:articleId, :title, :content, :categoryId, :startDate, :dueDate, NOW(), NOW())", nativeQuery = true)
    void insertArticleDirectly(@Param("articleId") Integer articleId,
                               @Param("title") String title,
                               @Param("content") String content,
                               @Param("categoryId") Integer categoryId,
                               @Param("startDate") LocalDate startDate,
                               @Param("dueDate") LocalDate dueDate);
}