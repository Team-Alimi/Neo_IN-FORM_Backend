package today.inform.inform.auth.service;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.auth.dto.response.TokenResponse;
import today.inform.inform.auth.service.GoogleTokenVerifier.GoogleUser;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.JwtTokenProvider;
import today.inform.inform.global.security.TokenRevocationStore;
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
    private final TokenRevocationStore tokenRevocationStore;
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
     *
     * <p><b>★ 권한 변경(ADM-16)과 겹치는 순간을 막아야 합니다.</b>
     * 이 메서드는 DB 에서 role 을 읽어 Access Token 에 박습니다. 그런데 읽은 뒤 만들기 전에
     * 관리자가 강등을 커밋하고 {@link #revokeAccessTokens} 를 부르면,
     * <b>강등 이후에 발급됐지만 강등 이전 권한이 담긴</b> 토큰이 만들어집니다.
     * 무효화 판정이 {@code iat <= revokedBefore} 인 초 단위 비교라 이 토큰은 기준보다 늦어 통과합니다.
     * 강등은 Refresh Token 을 일부러 남기므로, 강등 대상이 이 경로를 계속 두드릴 수 있습니다.
     *
     * <p>그래서 <b>시작 시각</b>을 들고 있다가, 토큰을 만든 뒤 그 시각 기준으로 무효화가 있었는지
     * 다시 확인합니다. 있었다면 방금 만든 토큰은 신뢰할 수 없으므로 버리고 재로그인을 요구합니다.
     * 강등과 재발급이 같은 순간에 겹칠 때만 걸리므로 정상 사용자가 만날 일은 사실상 없습니다.
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Instant startedAt = Instant.now();
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

        // 위 javadoc 의 강등 경합. 이 트랜잭션이 role 을 읽은 뒤 무효화가 들어왔다면
        // 방금 만들 토큰에는 낡은 권한이 담겼을 수 있습니다. 통과시키면 만료까지 살아남습니다.
        if (tokenRevocationStore.isRevoked(userId, startedAt)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFRESH_TOKEN, "세션이 만료되었습니다. 다시 로그인해 주세요.");
        }

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

    /**
     * AUTH-04 전체 기기 로그아웃. 회원 탈퇴(USER-03)도 이 경로를 씁니다.
     *
     * <p><b>Refresh Token 을 지우는 것만으로는 부족합니다.</b>
     * 이미 발급된 Access Token 은 stateless 라 만료(1시간)까지 그대로 통합니다.
     * "모든 기기에서 로그아웃" 을 눌렀는데 다른 기기가 한 시간 더 동작하면 기능이 거짓말을 하는 것이고,
     * 탈퇴한 사용자가 그 사이 댓글을 계속 남길 수 있으면 탈퇴가 탈퇴가 아닙니다.
     *
     * <p>그래서 남은 Access Token 도 함께 무효화합니다.
     * 기준이 <b>시각</b>이라 직후에 다시 로그인해 받은 새 토큰은 정상 동작합니다.
     */
    @Transactional(readOnly = true)
    public void logoutAll(Long userId) {
        refreshTokenStore.revokeAll(userId);
        tokenRevocationStore.revokeAllBefore(userId);
    }

    /**
     * ADM-16 권한 변경 반영. <b>로그아웃은 아닙니다.</b>
     *
     * <p>{@code AuthPrincipal} 의 권한은 Access Token 안에 박혀 있고 요청마다 DB 를 다시 읽지 않습니다.
     * 그래서 강등된 관리자가 <b>최대 1시간 동안 관리자 API 를 계속 씁니다.</b>
     * 권한을 뺏는 조작이 한 시간 뒤에 듣는다면 뺏은 것이 아닙니다.
     *
     * <p>{@link #logoutAll} 과 달리 Refresh Token 은 <b>남깁니다.</b>
     * 남은 Access Token 만 끊으면 클라이언트가 한 번 401 을 받고 조용히 refresh 해서
     * <b>새 권한이 담긴 토큰</b>을 받습니다({@link #refresh} 가 사용자를 다시 읽습니다).
     * 다시 로그인시킬 이유가 없습니다 — 권한이 바뀌었을 뿐 세션이 위험해진 것은 아닙니다.
     */
    @Transactional(readOnly = true)
    public void revokeAccessTokens(Long userId) {
        tokenRevocationStore.revokeAllBefore(userId);
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
