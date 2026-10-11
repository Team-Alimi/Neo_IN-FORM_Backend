package today.inform.inform.article.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inform.summary")
public record ArticleSummaryProperties(
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("2") int concurrency,
        @DefaultValue("20") int queueCapacity,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("60s") Duration cooldown,
        @DefaultValue("90s") Duration lockTtl,
        @DefaultValue("70s") Duration jobTimeout,
        @DefaultValue("100") int rpm,
        @DefaultValue("250000") int tpm,
        @DefaultValue("512") int maxOutputTokens) {

    public ArticleSummaryProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(20)) > 0
                || concurrency < 1 || concurrency > 2 || queueCapacity < 1 || queueCapacity > 20
                || maxRetries < 0 || maxRetries > 2
                || cooldown == null || cooldown.isNegative() || cooldown.isZero()
                || jobTimeout == null || jobTimeout.isNegative() || jobTimeout.isZero()
                || jobTimeout.compareTo(Duration.ofSeconds(70)) > 0
                || lockTtl == null || lockTtl.compareTo(jobTimeout) <= 0
                || rpm < 1 || rpm > 100 || tpm < 1 || tpm > 250000
                || maxOutputTokens < 1 || maxOutputTokens > 512) {
            throw new IllegalArgumentException("Invalid article summary limits");
        }
    }
}
