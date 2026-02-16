package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.NotificationResponse;
import today.inform.inform_backend.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(notificationService.getNotifications(userId));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal Integer userId
    ) {
        long count = notificationService.getUnreadCount(userId);
        return ApiResponse.success(Map.of("unread_count", count));
    }

    @PatchMapping("/{notification_id}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Integer userId,
            @PathVariable(name = "notification_id") Integer notificationId
    ) {
        notificationService.markAsRead(userId, notificationId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @AuthenticationPrincipal Integer userId
    ) {
        notificationService.markAllAsRead(userId);
        return ApiResponse.success(null);
    }
}
