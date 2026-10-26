package today.inform.inform.notification.service;

import today.inform.inform.global.config.WebConfig;

/**
 * NTF-05 수신거부 경로.
 *
 * <p>상수로 묶어 두는 이유 — 이 경로는 <b>세 곳</b>에서 같아야 합니다.
 * 메일 본문에 박히는 링크, 컨트롤러의 매핑, {@code SecurityConfig} 의 인증 예외.
 * 하나라도 어긋나면 링크가 404 나 401 이 되고, 그 사실은
 * <b>메일을 받은 사용자가 눌러 본 뒤에야</b> 드러납니다.
 *
 * <p>두 상수로 나뉘어 있는 이유는 {@code WebConfig} 가 {@code @RestController} 에
 * {@code /api/v1} 을 자동으로 붙이기 때문입니다. 매핑에는 접두사가 없는 쪽을,
 * 메일 링크에는 붙은 쪽을 씁니다. <b>붙은 쪽을 손으로 적지 않고 유도</b>해서
 * 접두사가 바뀌어도 링크가 따라오게 합니다.
 */
public final class NotificationEmailPaths {

    /**
     * 컨트롤러 매핑과 {@code SecurityConfig} 용. 접두사 없음.
     *
     * <p>{@code SecurityConfig} 는 {@code P + UNSUBSCRIBE_RELATIVE} 로 등록합니다.
     */
    public static final String UNSUBSCRIBE_RELATIVE = "/notifications/email/unsubscribe";

    /**
     * 메일 본문에 들어가는 경로. 앞에 호스트만 붙이면 완성됩니다.
     *
     * <p>{@code /api/v1/notifications/email/unsubscribe}
     */
    public static final String UNSUBSCRIBE = WebConfig.API_PREFIX + UNSUBSCRIBE_RELATIVE;

    private NotificationEmailPaths() {
    }
}
