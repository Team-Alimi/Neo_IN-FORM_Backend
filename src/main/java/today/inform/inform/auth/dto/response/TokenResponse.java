package today.inform.inform.auth.dto.response;

import today.inform.inform.user.entity.UserRole;

/**
 * AUTH-01 / AUTH-02 응답.
 *
 * <p>{@code onboardingCompleted} 는 프론트가 온보딩 화면으로 보낼지 판단하는 값입니다.
 * 미완료 사용자는 다음 접속마다 온보딩으로 유도합니다(차단이 아니라 유도).
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        UserRole role,
        boolean onboardingCompleted) {
}
