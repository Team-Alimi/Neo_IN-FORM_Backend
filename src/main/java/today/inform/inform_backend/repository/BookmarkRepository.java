package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Bookmark;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;

import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Integer> {
    Optional<Bookmark> findByUserAndArticleTypeAndArticleId(User user, VendorType articleType, Integer articleId);
    boolean existsByUserAndArticleTypeAndArticleId(User user, VendorType articleType, Integer articleId);
}
