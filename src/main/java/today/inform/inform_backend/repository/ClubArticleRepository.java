package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.ClubArticle;

public interface ClubArticleRepository extends JpaRepository<ClubArticle, Integer>, ClubArticleRepositoryCustom {
}
