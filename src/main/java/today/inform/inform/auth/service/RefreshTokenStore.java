package today.inform.inform.auth.service;

import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 세션 저장소. <b>Redis 가 유일한 저장소이며 DB 테이블이 없습니다.</b>
 *
 * <p>POLICY 1장의 "Redis 는 SoT 가 아니다"는 조회 cache / ranking cache 영역에 한정된 표현이고,
 * 세션은 이 영역 밖입니다. Redis 가 날아가면 전원 재로그인이며, 그건 감수하는 동작입니다.
 *
 * <pre>
 * inform:refresh:{tokenId}       -> userId    유효한 세션
 * inform:refresh:used:{tokenId}  -> userId    rotation 으로 소비됨 (탈취 탐지용)
 * inform:refresh:user:{userId}   -> Set       그 사용자의 살아있는 tokenId 전부
 * </pre>
 *
 * <p><b>왜 used 를 따로 남기는가</b>
 * rotation 때 옛 tokenId 를 지우기만 하면, 그 토큰으로 다시 요청이 왔을 때
 * "만료된 것"인지 "탈취범이 쓰는 것"인지 구별할 수 없습니다.
 * 소비된 토큰을 표시해 두면 재사용을 명확히 탐지할 수 있고,
 * 탐지 시 해당 사용자의 전체 세션을 끊습니다.
 *
 * <p>기기별로 tokenId 가 다르므로 PC 에서 로그인해도 폰이 로그아웃되지 않습니다.
 * (v1 은 email 을 키로 써서 계정당 세션이 하나였습니다)
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String ACTIVE_PREFIX = "inform:refresh:";
    private static final String USED_PREFIX = "inform:refresh:used:";
    private static final String USER_SET_PREFIX = "inform:refresh:user:";

    private final StringRedisTemplate redis;

    public void save(Long userId, String tokenId, Duration ttl) {
        redis.opsForValue().set(ACTIVE_PREFIX + tokenId, String.valueOf(userId), ttl);
        redis.opsForSet().add(USER_SET_PREFIX + userId, tokenId);
        // 세션 집합도 함께 만료시켜야 탈퇴/장기 미접속 계정의 키가 영원히 남지 않습니다.
        redis.expire(USER_SET_PREFIX + userId, ttl);
    }

    /** 유효한 세션인지. rotation 전에 확인합니다. */
    public boolean isActive(String tokenId) {
        return Boolean.TRUE.equals(redis.hasKey(ACTIVE_PREFIX + tokenId));
    }

    /** 이미 rotation 으로 소비된 토큰인지. true 면 탈취로 간주합니다. */
    public boolean isUsed(String tokenId) {
        return Boolean.TRUE.equals(redis.hasKey(USED_PREFIX + tokenId));
    }

    /**
     * rotation — 옛 토큰을 소비 처리하고 새 토큰을 발급합니다.
     * 소비 표시의 TTL 은 refresh 유효기간과 같습니다. 그보다 짧으면
     * 탈취범이 만료를 기다렸다가 재사용할 수 있습니다.
     */
    public void rotate(Long userId, String oldTokenId, String newTokenId, Duration ttl) {
        redis.delete(ACTIVE_PREFIX + oldTokenId);
        redis.opsForSet().remove(USER_SET_PREFIX + userId, oldTokenId);
        redis.opsForValue().set(USED_PREFIX + oldTokenId, String.valueOf(userId), ttl);
        save(userId, newTokenId, ttl);
    }

    /** 현재 기기만 로그아웃. 소비 표시를 남기지 않습니다 — 정상 종료이지 탈취가 아닙니다. */
    public void revoke(Long userId, String tokenId) {
        redis.delete(ACTIVE_PREFIX + tokenId);
        redis.opsForSet().remove(USER_SET_PREFIX + userId, tokenId);
    }

    /** 전체 기기 로그아웃. 탈취 탐지 시에도 호출합니다. */
    public void revokeAll(Long userId) {
        String setKey = USER_SET_PREFIX + userId;
        Set<String> tokenIds = redis.opsForSet().members(setKey);
        if (tokenIds != null && !tokenIds.isEmpty()) {
            redis.delete(tokenIds.stream().map(ACTIVE_PREFIX::concat).toList());
        }
        redis.delete(setKey);
    }
}
