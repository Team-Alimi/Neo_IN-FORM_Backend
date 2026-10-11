package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class ArticleSummaryGuardTest {
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private ArticleSummaryGuard guard;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stopRedis() {
        if (connections != null) { connections.destroy(); }
        REDIS.stop();
    }

    @BeforeEach
    void clearTestKeys() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        guard = new ArticleSummaryGuard(redis, SummaryTestFixtures.properties());
    }

    @Test
    @DisplayName("동일 게시글의 동시 요청 중 한 개만 lock 을 얻는다")
    void onlyOneConcurrentOwner() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<String>> requests = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                requests.add(pool.submit(() -> guard.acquire(1L)));
            }
            int admitted = 0;
            for (Future<String> request : requests) {
                if (request.get() != null) { admitted++; }
            }
            assertThat(admitted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("lock 은 TTL 이 있고 다른 소유자가 해제할 수 없으며 실패 cooldown 을 적용한다")
    void checksOwnerAndCooldown() {
        String owner = guard.acquire(1L);
        assertThat(owner).isNotNull();
        assertThat(redis.getExpire("inform:article-summary:{1}:lock")).isBetween(1L, 90L);
        guard.release(1L, "different-owner");
        assertThat(guard.owns(1L, owner)).isTrue();
        guard.cooldown(1L, owner);
        guard.release(1L, owner);
        assertThat(guard.acquire(1L)).isNull();
        assertThat(redis.getExpire("inform:article-summary:{1}:cooldown")).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("최초 호출과 재시도 모두 최근 60초 RPM 100 에 포함한다")
    void limitsRpmAndExpiresOldAttempts() {
        for (int i = 0; i < 100; i++) {
            assertThat(guard.reserve(1)).isNotNull();
        }
        assertThat(guard.reserve(1)).isNull();
        for (String id : redis.opsForZSet().range("inform:article-summary:{upstage}:attempts", 0, -1)) {
            redis.opsForZSet().add("inform:article-summary:{upstage}:attempts", id,
                    System.currentTimeMillis() - 61000);
        }
        assertThat(guard.reserve(1)).isNotNull();
        assertThat(redis.opsForHash().size("inform:article-summary:{upstage}:tokens")).isEqualTo(1);
    }

    @Test
    @DisplayName("입력·최대 출력을 예약하며 usage 와 timeout 예약도 TPM 에 남긴다")
    void limitsTpmWithoutRefundingUnknownUsage() {
        String first = guard.reserve(200000);
        assertThat(first).isNotNull();
        assertThat(guard.reserve(50001)).isNull();
        guard.recordUsage(first, 240000);
        assertThat(guard.reserve(10001)).isNull();
        assertThat(guard.reserve(10000)).isNotNull();
        // usage 가 적더라도 예약을 줄이지 않아 token 추정 오차에 대한 여유를 유지합니다.
        guard.recordUsage(first, 1);
        assertThat(guard.reserve(1)).isNull();
        assertThat(guard.reserve(250001)).isNull();
    }

    @Test
    @DisplayName("lock TTL 만료 후 새 소유자를 이전 작업이 해제하지 못한다")
    void expiredOwnerCannotReleaseReplacement() throws Exception {
        String old = guard.acquire(1L);
        redis.expire("inform:article-summary:{1}:lock", java.time.Duration.ofMillis(1));
        Thread.sleep(20);
        String replacement = guard.acquire(1L);
        assertThat(replacement).isNotNull().isNotEqualTo(old);
        guard.release(1L, old);
        assertThat(guard.owns(1L, replacement)).isTrue();
        assertThat(guard.owns(1L, old)).isFalse();
    }
}
