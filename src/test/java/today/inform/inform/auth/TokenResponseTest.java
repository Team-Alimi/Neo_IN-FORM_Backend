package today.inform.inform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import today.inform.inform.auth.dto.response.TokenResponse;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;

/**
 * 로그인·재발급 응답이 실어 보내는 사용자 정보.
 *
 * <p>여기서 검증하는 것은 "어떤 값으로 온보딩 화면을 고르는가" 입니다.
 * 잘못 고르면 관심사가 빈 사용자가 빈 피드를 보게 되는데, 서버는 200 을 주고 있어
 * 아무 데도 기록이 남지 않습니다.
 */
class TokenResponseTest {

    @Test
    @DisplayName("로그인 응답에 사용자 정보가 함께 실린다 — 이름 한 줄 띄우려고 /users/me 를 또 부르지 않도록")
    void carriesUserInfo() {
        User user = User.create("Kim@inha.ac.kr", "김인하");

        TokenResponse response = TokenResponse.of("access", "refresh", user);

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.userInfo().email())
                .as("이메일은 저장 시점에 소문자로 정규화됩니다")
                .isEqualTo("kim@inha.ac.kr");
        assertThat(response.userInfo().name()).isEqualTo("김인하");
        assertThat(response.userInfo().role())
                .as("Spring Security 의 ROLE_ 접두사는 API 계약에 넣지 않습니다")
                .isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("★ 온보딩을 마치지 않은 사용자는 몇 번째 로그인이든 false 다")
    void onboardingFlagReflectsRealStateNotAccountAge() {
        User user = User.create("dropout@inha.ac.kr", "중도이탈");

        // 가입만 하고 온보딩 도중에 앱을 껐다. 다음 날 다시 로그인해도 계정은 더 이상 새것이 아니다.
        assertThat(TokenResponse.of("a", "r", user).userInfo().onboardingCompleted())
                .as("""
                        'is_new_user' 였다면 여기서 false 가 나와 기존 사용자로 취급됐을 자리입니다.
                        그 사람은 관심사가 비어 있어 메인 피드가 텅 빈 채로 열립니다.""")
                .isFalse();

        user.completeOnboarding();

        assertThat(TokenResponse.of("a", "r", user).userInfo().onboardingCompleted()).isTrue();
    }
}
