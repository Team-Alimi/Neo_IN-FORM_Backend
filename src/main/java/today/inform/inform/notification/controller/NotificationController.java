package today.inform.inform.notification.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.notification.dto.response.NotificationResponse;
import today.inform.inform.notification.service.NotificationService;

/**
 * NTF-01 ~ NTF-04. 전부 로그인이 필요합니다.
 *
 * <p>메일 수신 여부는 여기가 아니라 {@code PATCH /users/me} 에서 켜고 끕니다 —
 * 설정 화면 하나로 끝나는 편이 낫다고 판단해, 메일 하단의 비로그인 수신거부 링크는 두지 않습니다.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** NTF-01 목록. 최신순 고정입니다. */
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(
                notificationService.list(principal.userId(), pageable)));
    }

    /**
     * NTF-02 배지 개수.
     *
     * <p>목록과 분리한 이유 — 배지는 화면 어디에나 떠 있어서 자주 불립니다.
     * 목록을 통째로 받아 세면 매번 알림 본문까지 실어 나르게 됩니다.
     * 이쪽은 부분 인덱스만 훑습니다.
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(
                Map.of("unread_count", notificationService.unreadCount(principal.userId())));
    }

    /** NTF-03 개별 읽음. 멱등. */
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long notificationId) {
        notificationService.markRead(principal.userId(), notificationId);
        return ApiResponse.success(null);
    }

    /**
     * NTF-04 전체 읽음.
     *
     * <p>이번에 처리된 개수를 돌려줍니다. 클라이언트가 배지를 다시 조회하지 않고
     * 바로 0 으로 내릴 수 있고, 실제로 몇 개가 정리됐는지도 보여 줄 수 있습니다.
     */
    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(
                Map.of("read_count", notificationService.markAllRead(principal.userId())));
    }
}
