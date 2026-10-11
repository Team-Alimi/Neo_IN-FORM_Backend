package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import today.inform.inform.article.config.ArticleSummaryConfig;
import today.inform.inform.article.config.ArticleSummaryProperties;

class ArticleSummaryConfigTest {

    @Test
    @DisplayName("기본 제한을 bind 하며 credential 없는 client 는 비활성화된다")
    void bindsDefaultsWithoutCredentials() {
        new ApplicationContextRunner()
                .withUserConfiguration(ArticleSummaryConfig.class, UpstageArticleSummaryClient.class)
                .withPropertyValues("UPSTAGE_API_KEY=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ArticleSummaryProperties limits = context.getBean(ArticleSummaryProperties.class);
                    assertThat(limits.timeout()).isEqualTo(Duration.ofSeconds(20));
                    assertThat(limits.maxRetries()).isEqualTo(2);
                    assertThat(limits.rpm()).isEqualTo(100);
                    assertThat(limits.tpm()).isEqualTo(250000);
                    assertThat(context.getBean(ArticleSummaryClient.class).isEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("환경 설정으로 대기열과 작업 제한 시간을 상한보다 늘릴 수 없다")
    void rejectsExpandedLimits() {
        for (String property : new String[] {
                "inform.summary.queue-capacity=21", "inform.summary.job-timeout=71s"}) {
            new ApplicationContextRunner().withUserConfiguration(ArticleSummaryConfig.class)
                    .withPropertyValues(property)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    @DisplayName("두 worker 와 대기열 20건이 차면 요청 스레드에서 실행하지 않고 거절한다")
    void rejectsOverflowInsteadOfRunningOnCaller() throws Exception {
        ThreadPoolTaskExecutor executor = new ArticleSummaryConfig()
                .articleSummaryExecutor(SummaryTestFixtures.properties());
        executor.initialize();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocking = () -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        try {
            executor.execute(blocking);
            executor.execute(blocking);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 20; i++) { executor.execute(blocking); }
            assertThat(executor.getActiveCount()).isEqualTo(2);
            assertThatThrownBy(() -> executor.execute(blocking)).isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("HTTP 응답 header 이후 body 가 멈춰도 호출 timeout 으로 종료한다")
    void timesOutWhileReadingBody() throws Exception {
        ArticleSummaryProperties defaults = SummaryTestFixtures.properties();
        ArticleSummaryProperties fast = new ArticleSummaryProperties(Duration.ofMillis(200),
                defaults.concurrency(), defaults.queueCapacity(), defaults.maxRetries(), defaults.cooldown(),
                defaults.lockTtl(), defaults.jobTimeout(), defaults.rpm(), defaults.tpm(), defaults.maxOutputTokens());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try {
                Thread.sleep(1000);
                exchange.getResponseBody().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // timeout 으로 닫힌 테스트 연결입니다.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var http = new ArticleSummaryConfig().articleSummaryRestClient(fast).mutate()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
            UpstageArticleSummaryClient client = new UpstageArticleSummaryClient(http, "unit-test-placeholder", fast);
            long start = System.nanoTime();
            assertThatThrownBy(() -> client.generate(SummaryTestFixtures.SOURCE))
                    .isInstanceOf(ArticleSummaryClient.Failure.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(800));
        } finally {
            server.stop(0);
        }
    }
}
