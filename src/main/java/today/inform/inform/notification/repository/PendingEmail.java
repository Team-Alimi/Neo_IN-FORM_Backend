package today.inform.inform.notification.repository;

/**
 * 메일을 보내야 할 알림 한 건. 발송에 필요한 것만 담습니다.
 *
 * <p>엔티티로 받으면 수신 주소를 찍는 순간 알림 수만큼 users 조회가 나갑니다
 * ({@code CommentRow} 와 같은 이유입니다). 한 번의 join 으로 끝냅니다.
 *
 * @param notificationId 발송 성공 후 {@code email_sent_at} 을 찍을 대상
 * @param userId         수신거부 토큰에 서명할 대상. <b>주소가 아니라 id 입니다</b> —
 *                       토큰에 주소를 담으면 링크 자체가 개인정보가 되고,
 *                       메일 전달·브라우저 히스토리·웹서버 로그에 그대로 남습니다
 * @param email          수신 주소
 * @param title          메일 제목이 됩니다
 * @param message        메일 본문의 첫 줄이 됩니다
 */
public record PendingEmail(
        Long notificationId,
        Long userId,
        String email,
        String title,
        String message) {
}
