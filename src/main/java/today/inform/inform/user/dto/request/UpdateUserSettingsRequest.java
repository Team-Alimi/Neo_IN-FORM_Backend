package today.inform.inform.user.dto.request;

/**
 * USER-04 알림 수신 설정. null 이면 변경하지 않습니다(부분 수정).
 */
public record UpdateUserSettingsRequest(Boolean emailNotificationEnabled) {
}
