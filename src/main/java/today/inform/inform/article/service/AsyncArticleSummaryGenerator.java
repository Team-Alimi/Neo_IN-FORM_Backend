package today.inform.inform.article.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.article.config.ArticleSummaryProperties;
import today.inform.inform.article.repository.ArticleRepository;

/** 요청 스레드는 작업 등록만 합니다. AI 를 기다리는 동안 DB 트랜잭션을 유지하지 않습니다. */
@Slf4j
@Component
public class AsyncArticleSummaryGenerator implements ArticleSummaryGenerator {

    @FunctionalInterface
    interface Delay {
        void pause(long milliseconds) throws InterruptedException;
    }

    private final ArticleRepository repository;
    private final ArticleSummaryClient client;
    private final ArticleSummaryGuard guard;
    private final Executor executor;
    private final ArticleSummaryProperties properties;
    private final ArticleSummaryValidator validator;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final Delay delay;
    private final LongSupplier nanoTime;

    @Autowired
    public AsyncArticleSummaryGenerator(ArticleRepository repository, ArticleSummaryClient client,
            ArticleSummaryGuard guard, @Qualifier("articleSummaryExecutor") Executor executor,
            PlatformTransactionManager transactions, ArticleSummaryProperties properties,
            ArticleSummaryValidator validator) {
        this(repository, client, guard, executor, transactions, properties, validator, Thread::sleep);
    }

    AsyncArticleSummaryGenerator(ArticleRepository repository, ArticleSummaryClient client,
            ArticleSummaryGuard guard, Executor executor, PlatformTransactionManager transactions,
            ArticleSummaryProperties properties, ArticleSummaryValidator validator, Delay delay) {
        this(repository, client, guard, executor, transactions, properties, validator, delay, System::nanoTime);
    }

    AsyncArticleSummaryGenerator(ArticleRepository repository, ArticleSummaryClient client,
            ArticleSummaryGuard guard, Executor executor, PlatformTransactionManager transactions,
            ArticleSummaryProperties properties, ArticleSummaryValidator validator, Delay delay,
            LongSupplier nanoTime) {
        this.repository = repository;
        this.client = client;
        this.guard = guard;
        this.executor = executor;
        this.properties = properties;
        this.validator = validator;
        this.delay = delay;
        this.nanoTime = nanoTime;
        read = transaction(transactions, true);
        write = transaction(transactions, false);
    }

    private static TransactionTemplate transaction(PlatformTransactionManager transactions, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(readOnly);
        template.setTimeout(5);
        return template;
    }

    @Override
    public void requestSummary(Long articleId, OffsetDateTime sourceUpdatedAt) {
        if (articleId == null || articleId <= 0 || !client.isEnabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 관리자 재생성의 summary=NULL 이 커밋되기 전에 worker 가 읽으면 요청을 잃습니다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(articleId, sourceUpdatedAt);
                }
            });
        } else {
            submit(articleId, sourceUpdatedAt);
        }
    }

    private void submit(Long articleId, OffsetDateTime sourceUpdatedAt) {
        try {
            executor.execute(() -> generate(articleId, sourceUpdatedAt));
        } catch (RuntimeException e) {
            log.warn("요약 작업 등록 실패. articleId={}", articleId);
        }
    }

    private void generate(Long articleId, OffsetDateTime requestedUpdatedAt) {
        long deadline = nanoTime.getAsLong() + properties.jobTimeout().toNanos();
        String owner = null;
        try {
            owner = guard.acquire(articleId);
            if (owner == null) {
                return;
            }
            Optional<ArticleSummarySource> snapshot = read.execute(status -> repository.findById(articleId)
                    .filter(article -> article.getSummary() == null)
                    .map(ArticleSummarySource::from));
            if (snapshot == null || snapshot.isEmpty()) {
                return;
            }
            ArticleSummarySource source = snapshot.get();
            if (requestedUpdatedAt != null && !requestedUpdatedAt.equals(source.updatedAt())) {
                return;
            }
            for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (deadline - nanoTime.getAsLong() < properties.timeout().toNanos() + 1_000_000_000L) {
                    guard.cooldown(articleId, owner);
                    return;
                }
                if (!guard.owns(articleId, owner)) {
                    return;
                }
                String reservation = guard.reserve(client.reservationTokens(source));
                if (reservation == null) {
                    guard.cooldown(articleId, owner);
                    return;
                }
                try {
                    ArticleSummaryClient.Result result = client.generate(source);
                    guard.recordUsage(reservation, result.totalTokens());
                    String summary = validator.validate(result.content(), source);
                    if (!guard.owns(articleId, owner)) {
                        return;
                    }
                    long remainingSeconds = (deadline - nanoTime.getAsLong()) / 1_000_000_000L;
                    if (remainingSeconds < 1) {
                        return;
                    }
                    // 공유 template 을 변경하지 않고 해당 작업의 남은 시간만 저장에 허용합니다.
                    TransactionTemplate boundedWrite = transaction(write.getTransactionManager(), false);
                    boundedWrite.setTimeout((int) Math.min(5, remainingSeconds));
                    Integer applied = boundedWrite.execute(status -> repository.updateSummary(
                            source.id(), summary, source.updatedAt(), source.version()));
                    if (Integer.valueOf(0).equals(applied)) {
                        log.debug("요약 snapshot 변경 또는 캐시 선점으로 폐기. articleId={}", articleId);
                    }
                    return;
                } catch (ArticleSummaryClient.Failure e) {
                    if (!e.isRetryable() || attempt == properties.maxRetries()) {
                        guard.cooldown(articleId, owner);
                        log.warn("요약 생성 실패. articleId={}, attempts={}", articleId, attempt + 1);
                        return;
                    }
                    delay.pause(2000L * (attempt + 1));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            if (owner != null) {
                try {
                    guard.cooldown(articleId, owner);
                } catch (RuntimeException ignored) {
                    // Redis 장애 시에는 lock TTL 이 정리를 맡습니다.
                }
            }
            log.warn("요약 작업 실패. articleId={}", articleId);
        } finally {
            if (owner != null) {
                try {
                    guard.release(articleId, owner);
                } catch (RuntimeException e) {
                    log.warn("요약 lock 해제 실패. articleId={}", articleId);
                }
            }
        }
    }
}
