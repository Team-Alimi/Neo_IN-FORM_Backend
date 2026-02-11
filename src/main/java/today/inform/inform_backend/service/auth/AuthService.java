package today.inform.inform_backend.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.config.jwt.JwtProperties;
import today.inform.inform_backend.config.jwt.JwtProvider;
import today.inform.inform_backend.dto.GoogleUserInfo;
import today.inform.inform_backend.dto.LoginResponse;
import today.inform.inform_backend.entity.RefreshToken;
import today.inform.inform_backend.entity.SocialType;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.repository.RefreshTokenRepository;
import today.inform.inform_backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuthProviderFactory providerFactory;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResponse login(SocialType socialType, String token) {
        OAuthProvider provider = providerFactory.getProvider(socialType);
        GoogleUserInfo userInfo = provider.verifyToken(token);

        boolean isNewUser = false;
        User user = userRepository.findByEmail(userInfo.getEmail()).orElse(null);

        if (user == null) {
            user = User.builder()
                    .email(userInfo.getEmail())
                    .name(userInfo.getName())
                    .build();
            userRepository.save(user);
            isNewUser = true;
        } else {
            // 기존 유저 이름 업데이트
            user.updateName(userInfo.getName());
        }

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getEmail());

        saveRefreshToken(user.getEmail(), refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isNewUser(isNewUser)
                .userInfo(LoginResponse.UserInfo.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .majorId(user.getMajor() != null ? user.getMajor().getVendorId() : null)
                        .build())
                .build();
    }

    private void saveRefreshToken(String email, String token) {
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .email(email)
                .token(token)
                .expiration(jwtProperties.getRefreshExpiration() / 1000)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);
    }
}
