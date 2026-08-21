package today.inform.inform.auth.dto.response;

import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;

/**
 * AUTH-01 로그인 · AUTH-02 재발급 응답.
 *
 * <p><b>왜 사용자 정보를 함께 싣는가</b>
 * 싣지 않으면 클라이언트가 로그인 직후 {@code GET /users/me} 를 한 번 더 불러야
 * 이름 한 줄을 띄울 수 있습니다. 앱 첫 진입은 체감 속도가 가장 중요한 화면인데
 * 거기에 왕복을 하나 더 넣게 됩니다. 두 경로 모두 이미 {@link User} 를 손에 들고 있으므로
 * 추가 조회 없이 그냥 실어 보냅니다.
 *
 * <p><b>왜 {@code isNewUser} 가 아니라 {@code onboardingCompleted} 인가</b>
 * "계정이 방금 만들어졌나" 와 "이 사람이 설정을 끝냈나" 는 다른 질문입니다.
 * 가입 직후 온보딩 도중에 앱을 껐다가 다음 날 다시 들어오면 계정은 더 이상 새것이 아니지만
 * 관심사는 여전히 비어 있습니다. 전자로 화면을 고르면 그 사람은 <b>빈 피드</b>를 보게 되고,
 * 앱이 고장 났다고 판단합니다. 후자는 {@code onboarding_completed_at} 에서 나오는 실제 상태라
 * 몇 번째 로그인이든 항상 맞습니다.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        UserInfo userInfo) {

    public static TokenResponse of(String accessToken, String refreshToken, User user) {
        return new TokenResponse(accessToken, refreshToken, UserInfo.from(user));
    }

    /**
     * 로그인 직후 화면을 그리는 데 필요한 최소 정보.
     *
     * <p>프로필 화면에 필요한 나머지(알림 수신 설정 등)는 {@code GET /users/me} 에 있습니다.
     * 여기에 다 넣으면 로그인 응답이 프로필 API 를 흉내 내기 시작하고, 둘이 갈라집니다.
     *
     * @param role               {@code USER} 또는 {@code ADMIN}. Spring Security 의 {@code ROLE_}
     *                           접두사는 붙이지 않습니다 — 그건 프레임워크 내부 표기이지 API 계약이 아닙니다
     * @param onboardingCompleted {@code false} 면 온보딩 화면으로 보냅니다
     */
    public record UserInfo(
            Long userId,
            String email,
            String name,
            UserRole role,
            boolean onboardingCompleted) {

        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole(),
                    user.isOnboardingCompleted());
        }
    }
}
