package today.inform.inform_backend.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.config.jwt.JwtProperties;
import today.inform.inform_backend.config.jwt.JwtProvider;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.*;
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
                        .major(user.getMajor() != null ? VendorListResponse.builder()
                                .vendorId(user.getMajor().getVendorId())
                                .vendorName(user.getMajor().getVendorName())
                                .vendorInitial(user.getMajor().getVendorInitial())
                                .vendorType(user.getMajor().getVendorType().name())
                                .build() : null)
                        .build())
                .build();
    }

    @Transactional
    public TokenRefreshResponse reissueToken(String refreshToken) {
        // 1. Refresh Token 유효성 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "유효하지 않은 Refresh Token입니다.");
        }

        // 2. 토큰에서 정보 추출
        var claims = jwtProvider.getClaims(refreshToken);
        String email = claims.get("email", String.class);
        Integer userId = Integer.parseInt(claims.getSubject());

        // 3. Redis에 저장된 토큰과 일치하는지 확인
        RefreshToken storedToken = refreshTokenRepository.findById(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ID_TOKEN, "만료된 Refresh Token입니다. 다시 로그인해주세요."));

        if (!storedToken.getToken().equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "유효하지 않은 Refresh Token입니다.");
        }

        // 4. 새로운 토큰 쌍 발급 (RT Rotation)
        String newAccessToken = jwtProvider.createAccessToken(userId, email);
        String newRefreshToken = jwtProvider.createRefreshToken(userId, email);

        saveRefreshToken(email, newRefreshToken);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    public void logout(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        refreshTokenRepository.deleteById(user.getEmail());
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
