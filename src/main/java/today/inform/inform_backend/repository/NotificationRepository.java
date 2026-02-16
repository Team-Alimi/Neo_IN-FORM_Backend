package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Notification;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
    long countByUserAndIsReadFalse(User user);
    boolean existsByUserAndArticleTypeAndArticleIdAndCreatedAtAfter(User user, VendorType articleType, Integer articleId, LocalDateTime createdAt);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :targetDate")
    void deleteByCreatedAtBefore(@Param("targetDate") LocalDateTime targetDate);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Notification n WHERE n.user = :user")
    void deleteAllByUser(@org.springframework.data.repository.query.Param("user") User user);
}
