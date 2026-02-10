package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.SchoolArticle;

public interface SchoolArticleRepository extends JpaRepository<SchoolArticle, Integer>, SchoolArticleRepositoryCustom {
    // 기존 @Query 로직은 SchoolArticleRepositoryImpl로 이동되었습니다.
}