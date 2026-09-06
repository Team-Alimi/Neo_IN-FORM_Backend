package today.inform.inform.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * DB 가 필요한 테스트의 공통 기반.
 *
 * <p><b>공식 postgres 이미지를 쓰지 않습니다.</b> {@code docker/db/Dockerfile} 로 직접 빌드합니다.
 * 이유는 {@code pg_bigm} 입니다. 이 확장이 없으면
 * <ul>
 *   <li>V1 이 확장 존재 확인에서 명시적으로 실패하고,</li>
 *   <li>{@code idx_articles_search_bigm} 를 만들 수 없어 V4 도 실패합니다.</li>
 * </ul>
 * 즉 확장 없는 이미지로는 스키마 자체가 올라가지 않습니다. 우회할 방법이 없어서 빌드합니다.
 * 첫 실행은 소스 빌드 때문에 몇 분 걸리고, 이후로는 Docker 레이어 캐시로 즉시 뜹니다.
 *
 * <p>컨테이너는 {@code static} 이라 클래스마다 새로 뜨지 않고 JVM 당 한 번만 뜹니다.
 * Flyway 도 그 한 번만 돌므로 V1~V5 전체 경로가 매 테스트 실행마다 검증됩니다.
 */
@Tag("integration")
@SpringBootTest
public abstract class IntegrationTest {

    /** initdb 스크립트가 inform_crawler 롤을 만들 때 쓰는 값. 테스트 전용입니다. */
    private static final String CRAWLER_PASSWORD = "testcrawlerpassword";

    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse(
                    new ImageFromDockerfile("inform-postgres-test", false)
                            .withFileFromPath(".", Path.of("docker", "db"))
                            .get())
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inform")
            .withUsername("inform")
            .withPassword("testpassword")
            // initdb 스크립트가 요구합니다. 없으면 컨테이너가 기동 중에 죽습니다.
            .withEnv("CRAWLER_PASSWORD", CRAWLER_PASSWORD);

    /**
     * Redis 도 컨테이너로 띄웁니다.
     *
     * <p><b>예전에는 {@code localhost:6379} 를 하드코딩했습니다.</b> "테스트에서 실제로 쓰지 않는
     * 값" 이라고 봤기 때문인데, 사실이 아니었습니다. {@code TokenRevocationStore} 가 Redis 를 쓰고
     * 토큰 무효화 테스트 5개가 그걸 검증합니다.
     *
     * <p>더 나빴던 건 조용히 실패했다는 점입니다. {@code TokenRevocationStore.isRevoked} 는
     * Redis 장애 시 <b>일부러 통과시킵니다</b>(가용성 우선). 그래서 Redis 가 없으면 예외 대신
     * "무효화 안 됨" 이 되고, 개발자 PC 에는 docker-compose 의 Redis 가 6379 에 떠 있어서
     * 로컬에서는 통과합니다. CI 에는 없으니 거기서만 5개가 실패했습니다.
     *
     * <p>컨테이너로 띄우면 어디서 돌리든 같고, 개발용 Redis 에 테스트 키를 남기지도 않습니다.
     */
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        DB.start();      // JVM 종료 시 Ryuk 이 정리합니다
        REDIS.start();
    }

    /**
     * 크롤러 롤로 여는 별도 커넥션.
     *
     * <p>크롤러는 앱을 거치지 않고 DB 에 직접 씁니다. 그 경로에 걸린 규칙
     * (컬럼 단위 GRANT, 재검수 강등, version 증가)은 <b>실제로 그 롤로 접속해야</b> 검증됩니다.
     * {@code inform} 계정으로 흉내 내면 {@code session_user} 가 달라 트리거가 아예 돌지 않습니다.
     *
     * <p>이 커넥션은 앱의 트랜잭션 밖입니다. 그래서 이걸 쓰는 테스트는
     * {@code @Transactional} 을 붙이면 안 됩니다 — 커밋되지 않은 데이터는 보이지 않습니다.
     */
    /**
     * 앱 계정으로 여는 별도 커넥션.
     *
     * <p>트랜잭션 두 개가 서로를 어떻게 막는지 보려면 커넥션이 물리적으로 달라야 합니다.
     * 이걸 쓰는 테스트도 {@code @Transactional} 을 붙이면 안 됩니다.
     */
    protected static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    protected static Connection crawlerConnection() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), "inform_crawler", CRAWLER_PASSWORD);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);

        // ★ 실제로 쓰입니다. TokenRevocationStore 가 이 Redis 에 무효화 시각을 기록하고,
        //   토큰 무효화 테스트 5개가 그 동작을 검증합니다. 하드코딩하면 안 됩니다.
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 아래는 애플리케이션이 요구하지만 이 테스트에서 실제로 쓰지 않는 값들.
        // 없으면 컨텍스트가 뜨지 않아 형태만 맞춰 채웁니다.
        registry.add("jwt.secret", () ->
                "dGVzdC1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWFueXdoZXJlLWVsc2UtcGFkZGluZy0xMjM0NTY3OA==");
        registry.add("google.oauth.client-id", () -> "test-client-id");
        registry.add("aws.s3.bucket", () -> "test-bucket");

        // 매핑이 스키마와 어긋나면 컨텍스트 기동에서 바로 실패해야 합니다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // 쿼리 횟수를 세는 테스트가 있어서 켭니다.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }
}
