package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.NotificationResponse;
import today.inform.inform_backend.entity.Notification;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.NotificationRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAsRead(Integer userId, Integer notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "알림을 찾을 수 없습니다."));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        notificationRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .filter(n -> !n.isRead())
                .forEach(Notification::markAsRead);
    }

    @Transactional
    public void createNotification(User user, String title, String message, VendorType articleType, Integer articleId) {
        // 오늘 이미 동일한 알림이 생성되었는지 확인 (중복 방지)
        LocalDateTime startOfToday = LocalDateTime.now().with(LocalTime.MIN);
        if (notificationRepository.existsByUserAndArticleTypeAndArticleIdAndCreatedAtAfter(user, articleType, articleId, startOfToday)) {
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .articleType(articleType)
                .articleId(articleId)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void createNotificationsBulk(List<Notification> notifications) {
        LocalDateTime startOfToday = LocalDateTime.now().with(LocalTime.MIN);
        
        List<Notification> filteredNotifications = notifications.stream()
                .filter(n -> !notificationRepository.existsByUserAndArticleTypeAndArticleIdAndCreatedAtAfter(
                        n.getUser(), n.getArticleType(), n.getArticleId(), startOfToday))
                .collect(Collectors.toList());

        if (!filteredNotifications.isEmpty()) {
            notificationRepository.saveAll(filteredNotifications);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .articleType(notification.getArticleType())
                .articleId(notification.getArticleId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
