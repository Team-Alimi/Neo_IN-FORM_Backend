package today.inform.inform.global.security;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이미 발급된 Access Token 을 시각 기준으로 무효화합니다.
 *
 * <h2>왜 필요한가</h2>
 * Access Token 은 stateless 라 발급 후에는 되돌릴 방법이 없습니다.
 * Refresh Token 만 지우면 <b>남은 Access Token 이 만료될 때까지(1시간) 그대로 통합니다.</b>
 * <ul>
 *   <li>탈퇴한 사용자가 그 사이 댓글·북마크를 계속 남길 수 있습니다.</li>
 *   <li>"모든 기기에서 로그아웃"(AUTH-04)을 눌러도 실제로는 1시간 뒤에야 끊깁니다.</li>
 * </ul>
 *
 * <h2>왜 "차단 여부"가 아니라 "시각"인가</h2>
 * 단순 차단 플래그로 두면 무효화한 사용자가 <b>TTL 이 끝날 때까지 다시 로그인할 수 없습니다.</b>
 * 기준 시각을 저장하고 그보다 먼저 발급된 토큰만 막으면,
 * 무효화 직후 다시 로그인해서 받은 새 토큰은 정상 동작합니다.
 * 나중에 권한 변경(ADM-16)에도 같은 장치를 쓸 수 있습니다 —
 * 강등된 관리자가 1시간 동안 관리자로 남는 문제가 같은 모양입니다.
 *
 * <h2>같은 초에 걸치면 막는 쪽을 택합니다</h2>
 * JWT 의 {@code iat} 는 초 단위라 무효화 시각과 같은 초에 발급된 토큰은
 * 앞뒤를 구분할 수 없습니다. 이때 통과시키면 <b>그 토큰은 남은 수명 내내(최대 1시간) 살아남습니다.</b>
 * 1초짜리 유예가 아니라 완전한 통과입니다.
 *
 * <p>반대로 막으면, 무효화와 같은 초에 다시 로그인한 사용자가 한 번 401 을 받습니다.
 * 1초 뒤 재시도로 저절로 풀립니다. 탈퇴 직후에는 애초에 재로그인이 불가능하므로
 * ({@code AuthService.refresh} 가 활성 사용자만 통과시킵니다) 이 경우도 거의 없습니다.
 *
 * <p>한쪽은 자동으로 낫고 다른 쪽은 한 시간을 갑니다. 막는 쪽으로 정합니다.
 */
@Slf4j
@Component
public class TokenRevocationStore {

    private static final String KEY_PREFIX = "inform:token-revoked:";

    private final StringRedisTemplate redis;
    private final Duration accessTokenValidity;

    public TokenRevocationStore(StringRedisTemplate redis, JwtTokenProvider jwtTokenProvider) {
        this.redis = redis;
        // Access Token 이 만료된 뒤에는 무효화 기록을 들고 있을 이유가 없습니다.
        this.accessTokenValidity = Duration.ofMillis(jwtTokenProvider.accessTokenValidityMs());
    }

    /** 이 시점 이전에 발급된 이 사용자의 Access Token 을 전부 무효화합니다. */
    public void revokeAllBefore(Long userId) {
        try {
            redis.opsForValue().set(
                    KEY_PREFIX + userId,
                    String.valueOf(Instant.now().getEpochSecond()),
                    accessTokenValidity);
        } catch (RuntimeException e) {
            // 남은 토큰이 만료까지 살아 있게 됩니다. 조용히 넘기면 안 됩니다.
            log.error("Access Token 무효화 실패. userId={}", userId, e);
        }
    }

    /**
     * 무효화된 토큰인지.
     *
     * <p><b>Redis 가 죽으면 통과시킵니다.</b> 막는 쪽을 택하면 Redis 장애가
     * 전체 서비스 중단이 됩니다. 이 장치가 막는 건 "탈퇴 후 최대 1시간" 처럼
     * 범위가 좁고 피해가 제한적인 경우라, 가용성을 우선합니다.
     */
    public boolean isRevoked(Long userId, Instant issuedAt) {
        if (issuedAt == null) {
            return false;
        }
        try {
            String revokedBefore = redis.opsForValue().get(KEY_PREFIX + userId);
            // 경계(같은 초)는 막는 쪽입니다. 위 클래스 주석 참조.
            return revokedBefore != null && issuedAt.getEpochSecond() <= Long.parseLong(revokedBefore);
        } catch (RuntimeException e) {
            log.error("Access Token 무효화 확인 실패. 통과시킵니다. userId={}", userId, e);
            return false;
        }
    }
}
