package today.inform.inform.user.dto.response;

import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;

/**
 * USER-01 내 프로필.
 *
 * <p>{@code onboardingCompleted} 가 false 면 프론트가 온보딩 화면으로 보냅니다.
 * 서버가 다른 API 를 막지는 않습니다 — 차단이 아니라 유도입니다.
 */
public record UserProfileResponse(
        Long id,
        String email,
        String name,
        UserRole role,
        boolean onboardingCompleted,
        boolean emailNotificationEnabled) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.isOnboardingCompleted(),
                user.isEmailNotificationEnabled());
    }
}
