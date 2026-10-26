package today.inform.inform.admin.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.notification.service.NotificationEmailSender;
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
    private final NotificationEmailSender notificationEmailSender;

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

    /**
     * NTF-05 알림 메일 발송을 지금 한 번 돌립니다. 주기 배치와 <b>완전히 같은 코드</b>입니다.
     *
     * <p><b>실제로 메일이 나갑니다.</b> 되돌릴 수 없습니다 — 누르기 전에
     * 대상이 누구인지 확인하세요. 대상은 "최근 {@code max-age} 안에 만들어졌고,
     * 아직 메일을 안 보냈고, 메일 수신을 켜 둔 활성 사용자" 의 알림입니다.
     *
     * <p><b>여러 번 눌러도 같은 메일이 두 번 가지 않습니다.</b>
     * 보낸 알림은 {@code email_sent_at} 이 찍혀 대상에서 빠집니다.
     * 그래서 두 번째 호출은 보통 {@code sent: 0} 입니다.
     *
     * <p>응답을 읽는 법.
     * <ul>
     *   <li>{@code targeted: 0} — 보낼 것이 없었거나, {@code MAIL_FROM} 이 없어
     *       발송이 비활성이거나, 다른 실행이 락을 들고 있었습니다.
     *       <b>셋이 구분되지 않으므로 로그를 보세요</b></li>
     *   <li>{@code failed > 0} — 그만큼은 {@code email_sent_at} 이 NULL 로 남아
     *       다음 주기에 재시도됩니다</li>
     *   <li>{@code duplicated > 0} — <b>같은 메일이 중복 발송됐을 수 있습니다.</b>
     *       배치 락이 제 역할을 못 하고 있다는 신호입니다</li>
     * </ul>
     *
     * <p>테스트 흐름은 {@code /deadline-run} 으로 알림을 만든 뒤 이것을 부르는 것입니다.
     * 수신 주소가 SES 에서 검증된 identity 여야 한다는 점을 잊지 마세요 —
     * 프로덕션 액세스가 승인되기 전에는 검증되지 않은 주소로 보낼 수 없습니다.
     */
    @PostMapping("/email-run")
    public ApiResponse<NotificationEmailSender.EmailRun> runEmailNotifications() {
        return ApiResponse.success(notificationEmailSender.sendPending());
    }
}
