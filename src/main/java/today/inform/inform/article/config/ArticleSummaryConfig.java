package today.inform.inform.article.config;

import java.net.http.HttpClient;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ArticleSummaryProperties.class)
public class ArticleSummaryConfig {

    @Bean
    public ThreadPoolTaskExecutor articleSummaryExecutor(ArticleSummaryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("article-summary-");
        // CallerRunsPolicy 는 상세 요청 스레드에서 AI 를 기다리게 하므로 사용하지 않습니다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    public RestClient articleSummaryRestClient(ArticleSummaryProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder().baseUrl("https://api.upstage.ai/v1")
                .requestFactory(factory).build();
    }
}
