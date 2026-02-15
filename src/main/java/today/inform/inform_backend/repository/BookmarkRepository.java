package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Bookmark;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Integer> {
    Optional<Bookmark> findByUserAndArticleTypeAndArticleId(User user, VendorType articleType, Integer articleId);
    boolean existsByUserAndArticleTypeAndArticleId(User user, VendorType articleType, Integer articleId);
    List<Bookmark> findAllByUserAndArticleTypeAndArticleIdIn(User user, VendorType articleType, List<Integer> articleIds);
    List<Bookmark> findAllByUserAndArticleTypeOrderByCreatedAtDesc(User user, VendorType articleType);
    
    void deleteAllByUserAndArticleType(User user, VendorType articleType);

    long countByArticleIdAndArticleType(Integer articleId, VendorType articleType);

    List<Bookmark> findAllByArticleTypeAndArticleId(VendorType articleType, Integer articleId);

    @org.springframework.data.jpa.repository.Query("SELECT b.articleId, COUNT(b) FROM Bookmark b WHERE b.articleId IN :articleIds AND b.articleType = :articleType GROUP BY b.articleId")
    List<Object[]> countByArticleIdsAndArticleType(@org.springframework.data.repository.query.Param("articleIds") List<Integer> articleIds, @org.springframework.data.repository.query.Param("articleType") VendorType articleType);
}
