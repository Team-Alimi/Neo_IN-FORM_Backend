package today.inform.inform.admin.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.notification.service.NotificationScheduler;

/**
 * 알림 배치 수동 실행. <b>{@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다</b>
 * ({@code SecurityConfig}).
 *
 * <p>있는 이유는 <b>테스트입니다.</b> 마감 알림은 매일 09:00 배치로만 생기므로,
 * 알림 화면을 만들거나 고치는 사람이 결과를 보려면 다음 날 아침까지 기다려야 했습니다.
 *
 * <p><b>정리 배치(30일 경과 삭제)는 열지 않았습니다.</b> 손으로 당겨 쓸 일이 없고
 * 되돌릴 수 없는 삭제라, 버튼으로 두면 얻는 것 없이 사고만 가능해집니다.
 */
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationScheduler notificationScheduler;

    /**
     * 마감 D-1 알림 생성을 지금 한 번 돌립니다. 09:00 배치와 <b>완전히 같은 코드</b>입니다.
     *
     * <p><b>여러 번 눌러도 안전합니다.</b> 같은 (사용자, 공지, 마감일) 조합은 유니크 키가
     * 막아서 두 번째부터는 {@code created: 0} 입니다.
     *
     * <p><b>날짜를 받지 않습니다.</b> 기준일은 언제나 "내일" 입니다 —
     * 임의 날짜를 허용하면 두 달 뒤 마감인 공지에 "마감이 하루 남았습니다" 를 보낼 수 있고,
     * 그건 실제 사용자에게 거짓을 알리는 것입니다.
     *
     * <p>그래서 테스트 흐름은 이렇습니다.
     * <ol>
     *   <li>공지를 하나 만들거나 골라 <b>마감일을 내일로</b> 둡니다</li>
     *   <li>테스트 계정으로 그 공지를 북마크합니다</li>
     *   <li>이 API 를 부릅니다 → 알림이 즉시 생깁니다</li>
     * </ol>
     *
     * <p>{@code created: 0} 이 오면 보통 3번이 아니라 1·2번이 어긋난 경우입니다 —
     * 마감일이 내일이 아니거나, 북마크가 없거나, 공지가 발행 상태가 아닙니다.
     * 응답의 {@code deadline} 으로 서버가 본 "내일" 을 확인하세요.
     */
    @PostMapping("/deadline-run")
    public ApiResponse<NotificationScheduler.DeadlineRun> runDeadlineNotifications() {
        return ApiResponse.success(notificationScheduler.runDeadlineNotifications());
    }
}
