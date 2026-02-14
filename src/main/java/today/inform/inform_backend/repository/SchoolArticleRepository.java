package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;
import java.util.List;

public interface SchoolArticleRepository extends JpaRepository<SchoolArticle, Integer>, SchoolArticleRepositoryCustom {
    List<SchoolArticle> findAllByDueDate(LocalDate dueDate);
}