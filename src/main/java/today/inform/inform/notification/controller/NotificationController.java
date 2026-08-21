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
import today.inform.inform.notification.service.UnsubscribeService;

/**
 * NTF-01 ~ NTF-04. 전부 로그인이 필요합니다.
 *
 * <p><b>{@code GET /notifications/unsubscribe} 하나만 예외</b>입니다.
 * 메일 하단 링크로 들어오는 <b>비로그인 경로</b>라 위조 방지를 HMAC 서명 토큰이 담당합니다
 * ({@code SecurityConfig} 에서 이 경로만 열려 있습니다).
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UnsubscribeService unsubscribeService;

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

    /**
     * USER-04 메일 수신거부. <b>비로그인 경로</b>입니다 — 메일 하단 링크의 목적지입니다.
     *
     * <p>본인 확인은 {@code token} 에 실린 HMAC 서명이 합니다. 서명이 없으면
     * 남의 번호를 넣어 타인의 수신을 끌 수 있습니다.
     *
     * <p><b>이미 꺼져 있어도 성공입니다.</b> 메일함에서 링크를 두 번 누르는 일은 흔한데,
     * 두 번째에 오류가 뜨면 사용자는 수신거부가 안 된 줄 알고 다시 시도합니다.
     *
     * <p>응답은 JSON 입니다. 명세는 "안내 페이지로" 라고 적고 있지만 프론트 주소가
     * 설정에 없어서 여기서 정할 수 없습니다. 리다이렉트로 바꾸려면 그 주소를 설정에 추가해야 합니다.
     */
    @GetMapping("/unsubscribe")
    public ApiResponse<Map<String, Boolean>> unsubscribe(@RequestParam(name = "token") String token) {
        unsubscribeService.unsubscribe(token);
        return ApiResponse.success(Map.of("email_notification_enabled", false));
    }
}
