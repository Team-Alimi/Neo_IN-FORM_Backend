package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;

import java.util.List;

public interface SchoolArticleVendorRepository extends JpaRepository<SchoolArticleVendor, Integer> {
    List<SchoolArticleVendor> findAllByArticleIn(List<SchoolArticle> articles);
    List<SchoolArticleVendor> findAllByArticle(SchoolArticle article);
}
