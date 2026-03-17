package today.inform.inform_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import today.inform.inform_backend.entity.ClubArticle;

import java.time.LocalDate;
import java.util.Optional;

public interface ClubArticleRepositoryCustom {
    Page<ClubArticle> findAllWithFilters(Integer vendorId, String keyword, LocalDate today, LocalDate dMinus5,
            Pageable pageable);

    Optional<ClubArticle> findByIdWithVendor(Integer articleId);
}
