package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Notification;
import today.inform.inform_backend.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
    long countByUserAndIsReadFalse(User user);
    boolean existsByUserAndArticleTypeAndArticleIdAndCreatedAtAfter(User user, VendorType articleType, Integer articleId, LocalDateTime createdAt);
}
