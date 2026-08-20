package today.inform.inform.notification.dto.response;

import java.time.OffsetDateTime;
import today.inform.inform.notification.entity.Notification;
import today.inform.inform.notification.entity.NotificationType;

/**
 * 알림 한 건.
 *
 * @param articleId 눌렀을 때 이동할 공지. {@code null} 이면 이동 대상이 없습니다
 * @param read      읽음 여부. 읽은 <b>시각</b>은 내보내지 않습니다 —
 *                  화면에 쓸 데가 없고, 사용자가 언제 무엇을 읽었는지는 남길 이유가 없습니다
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long articleId,
        boolean read,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getArticleId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
