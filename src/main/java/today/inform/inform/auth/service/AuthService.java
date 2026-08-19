package today.inform.inform.auth.service;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.auth.dto.response.TokenResponse;
import today.inform.inform.auth.service.GoogleTokenVerifier.GoogleUser;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.JwtTokenProvider;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserStatus;
import today.inform.inform.user.repository.UserRepository;

/**
 * AUTH-01 ~ AUTH-04.
 *
 * <p>도메인 참조 방향은 {@code auth -> user} 단방향입니다.
 * {@code user} 가 {@code auth} 를 부르지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;

    /**
     * AUTH-01 구글 로그인. 미가입자는 자동 가입합니다.
     */
    @Transactional
    public TokenResponse loginWithGoogle(String idToken) {
        GoogleUser googleUser = googleTokenVerifier.verify(idToken);

        User user = userRepository
                .findByEmailAndStatus(googleUser.email(), UserStatus.ACTIVE)
                .map(existing -> {
                    existing.updateProfileName(googleUser.name());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.create(googleUser.email(), googleUser.name())));

        return issueTokens(user);
    }

    /**
     * AUTH-02 재발급 (Rotation).
     *
     * <p>이전 refresh token 은 즉시 폐기하고 새 토큰을 발급합니다.
     * <b>이미 소비된 토큰이 다시 오면 탈취로 간주해 해당 사용자의 전체 세션을 끊습니다.</b>
     * 공격자가 훔친 토큰을 쓰는 순간 정상 사용자도 함께 로그아웃되지만,
     * 세션이 조용히 유지되는 것보다 낫습니다.
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Claims claims = jwtTokenProvider.parse(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());
        String tokenId = claims.getId();

        if (tokenId == null) {
            // access token 을 refresh 자리에 보낸 경우
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (refreshTokenStore.isUsed(tokenId)) {
            log.warn("Refresh token 재사용 감지 — 전체 세션을 무효화합니다. userId={}", userId);
            refreshTokenStore.revokeAll(userId);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "세션이 만료되었습니다. 다시 로그인해 주세요.");
        }

        if (!refreshTokenStore.isActive(tokenId)) {
            // 로그아웃되었거나 TTL 이 지난 세션
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newTokenId = JwtTokenProvider.newTokenId();
        refreshTokenStore.rotate(userId, tokenId, newTokenId, refreshTtl());

        return new TokenResponse(
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole()),
                jwtTokenProvider.createRefreshToken(user.getId(), newTokenId),
                user.getRole(),
                user.isOnboardingCompleted());
    }

    /** AUTH-03 현재 기기만 로그아웃. 다른 기기 세션은 유지됩니다. */
    @Transactional(readOnly = true)
    public void logout(String refreshToken) {
        Claims claims = jwtTokenProvider.parse(refreshToken);
        String tokenId = claims.getId();
        if (tokenId != null) {
            refreshTokenStore.revoke(Long.valueOf(claims.getSubject()), tokenId);
        }
    }

    /** AUTH-04 전체 기기 로그아웃. */
    @Transactional(readOnly = true)
    public void logoutAll(Long userId) {
        refreshTokenStore.revokeAll(userId);
    }

    private TokenResponse issueTokens(User user) {
        String tokenId = JwtTokenProvider.newTokenId();
        refreshTokenStore.save(user.getId(), tokenId, refreshTtl());
        return new TokenResponse(
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole()),
                jwtTokenProvider.createRefreshToken(user.getId(), tokenId),
                user.getRole(),
                user.isOnboardingCompleted());
    }

    private Duration refreshTtl() {
        return Duration.ofMillis(jwtTokenProvider.refreshTokenValidityMs());
    }
}
