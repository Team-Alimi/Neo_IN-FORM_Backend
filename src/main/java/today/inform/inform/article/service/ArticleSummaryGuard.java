package today.inform.inform.article.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import today.inform.inform.article.config.ArticleSummaryProperties;

/** 동일 원문 중복 생성과 backend 전용 Upstage 키의 rolling 60초 제한. */
@Component
public class ArticleSummaryGuard {

    private static final String ATTEMPTS = "inform:article-summary:{upstage}:attempts";
    private static final String TOKENS = "inform:article-summary:{upstage}:tokens";
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])
            if result then return 1 end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> COOLDOWN = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[2], '1', 'PX', ARGV[2])
                return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now - 60000)
            for _, id in ipairs(expired) do redis.call('HDEL', KEYS[2], id) end
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - 60000)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end
            local total = 0
            for _, value in ipairs(redis.call('HVALS', KEYS[2])) do
                total = total + tonumber(value)
            end
            if total + tonumber(ARGV[4]) > tonumber(ARGV[3]) then return 0 end
            redis.call('ZADD', KEYS[1], now, ARGV[1])
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], 120000)
            redis.call('PEXPIRE', KEYS[2], 120000)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> USAGE = new DefaultRedisScript<>("""
            local reserved = redis.call('HGET', KEYS[1], ARGV[1])
            if reserved and tonumber(ARGV[2]) > tonumber(reserved) then
                redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ArticleSummaryProperties properties;

    public ArticleSummaryGuard(StringRedisTemplate redis, ArticleSummaryProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String acquire(Long articleId) {
        String owner = UUID.randomUUID().toString();
        Long acquired = redis.execute(ACQUIRE, List.of(lockKey(articleId), cooldownKey(articleId)),
                owner, Long.toString(properties.lockTtl().toMillis()));
        return Long.valueOf(1).equals(acquired) ? owner : null;
    }

    public boolean owns(Long articleId, String token) {
        return token.equals(redis.opsForValue().get(lockKey(articleId)));
    }

    public void release(Long articleId, String token) {
        redis.execute(RELEASE, List.of(lockKey(articleId)), token);
    }

    public void cooldown(Long articleId, String token) {
        redis.execute(COOLDOWN, List.of(lockKey(articleId), cooldownKey(articleId)),
                token, Long.toString(properties.cooldown().toMillis()));
    }

    /** retry 도 매번 새 reservation 을 얻어야 합니다. 거절 시 외부 호출을 하지 않습니다. */
    public String reserve(long tokens) {
        if (tokens < 1 || tokens > properties.tpm()) {
            return null;
        }
        String reservation = UUID.randomUUID().toString();
        Long admitted = redis.execute(RESERVE, List.of(ATTEMPTS, TOKENS),
                reservation, Integer.toString(properties.rpm()),
                Integer.toString(properties.tpm()), Long.toString(tokens));
        return Long.valueOf(1).equals(admitted) ? reservation : null;
    }

    /** timeout/usage 누락은 예약을 유지합니다. 실제 사용량이 더 크면 상향 보정합니다. */
    public void recordUsage(String reservation, long tokens) {
        if (tokens > 0) {
            redis.execute(USAGE, List.of(TOKENS), reservation, Long.toString(tokens));
        }
    }

    private static String lockKey(Long articleId) {
        return "inform:article-summary:{" + articleId + "}:lock";
    }

    private static String cooldownKey(Long articleId) {
        return "inform:article-summary:{" + articleId + "}:cooldown";
    }
}
