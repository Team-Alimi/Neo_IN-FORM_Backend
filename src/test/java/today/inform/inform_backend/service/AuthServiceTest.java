package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.config.jwt.JwtProperties;
import today.inform.inform_backend.config.jwt.JwtProvider;
import today.inform.inform_backend.dto.GoogleUserInfo;
import today.inform.inform_backend.dto.LoginResponse;
import today.inform.inform_backend.entity.SocialType;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.repository.RefreshTokenRepository;
import today.inform.inform_backend.repository.UserRepository;
import today.inform.inform_backend.service.auth.AuthService;
import today.inform.inform_backend.service.auth.OAuthProvider;
import today.inform.inform_backend.service.auth.OAuthProviderFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private OAuthProviderFactory providerFactory;
    @Mock private OAuthProvider oauthProvider;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private JwtProperties jwtProperties;

    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("구글 로그인을 성공적으로 수행한다.")
    void login_Success() {
        // given
        String token = "valid-token";
        String email = "test@inha.edu";
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder().email(email).name("MVC리팩토링").build();
        User user = User.builder().userId(1).email(email).name("MVC리팩토링").build();

        given(providerFactory.getProvider(SocialType.GOOGLE)).willReturn(oauthProvider);
        given(oauthProvider.verifyToken(token)).willReturn(googleUserInfo);
        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(any(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(any(), anyString())).willReturn("refresh-token");

        // when
        LoginResponse response = authService.login(SocialType.GOOGLE, token);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(providerFactory).getProvider(SocialType.GOOGLE);
    }
}
