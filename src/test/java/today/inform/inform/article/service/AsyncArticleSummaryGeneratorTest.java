package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.repository.ArticleRepository;

class AsyncArticleSummaryGeneratorTest {
    private final ArticleRepository repository = mock(ArticleRepository.class);
    private final ArticleSummaryClient client = mock(ArticleSummaryClient.class);
    private final ArticleSummaryGuard guard = mock(ArticleSummaryGuard.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> waits = new ArrayList<>();
    private final AsyncArticleSummaryGenerator generator = new AsyncArticleSummaryGenerator(
            repository, client, guard, tasks::add, transactions, SummaryTestFixtures.properties(),
            new ArticleSummaryValidator(), waits::add);
    private Article article;

    @BeforeEach
    void fixture() {
        article = mock(Article.class);
        ArticleSummarySource source = SummaryTestFixtures.SOURCE;
        when(article.getId()).thenReturn(source.id());
        when(article.getTitle()).thenReturn(source.title());
        when(article.getContent()).thenReturn(source.content());
        when(article.getVersion()).thenReturn(source.version());
        when(article.getUpdatedAt()).thenReturn(source.updatedAt());
        when(repository.findById(1L)).thenReturn(Optional.of(article));
        when(repository.updateSummary(eq(1L), anyString(), eq(source.updatedAt()), eq(source.version()))).thenReturn(1);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(client.isEnabled()).thenReturn(true);
        when(client.reservationTokens(any())).thenReturn(2000L);
        when(client.generate(any())).thenReturn(new ArticleSummaryClient.Result(SummaryTestFixtures.CONTENT, 150L));
        when(guard.acquire(1L)).thenReturn("owner");
        when(guard.owns(1L, "owner")).thenReturn(true);
        when(guard.reserve(anyLong())).thenReturn("reservation");
    }

    private void run() {
        generator.requestSummary(1L, null);
        assertThat(tasks).hasSize(1);
        tasks.removeFirst().run();
    }

    @Test
    @DisplayName("요청은 AI 를 기다리지 않고 worker 가 최신 snapshot 을 조건부 저장한다")
    void submitsWithoutCallingAiOnRequestThread() {
        generator.requestSummary(1L, null);
        verify(client, never()).generate(any());
        verifyNoInteractions(repository);
        assertThat(tasks).hasSize(1);
        tasks.getFirst().run();
        verify(repository).updateSummary(1L,
                "재학생은 장학금을 신청할 수 있습니다.\n신청은 온라인으로 진행합니다.\n접수 내용은 공지를 확인해야 합니다.",
                SummaryTestFixtures.SOURCE.updatedAt(), 3L);
        verify(guard).recordUsage("reservation", 150L);
        verify(guard).release(1L, "owner");
        verify(transactions, times(2)).commit(any());
    }

    @Test
    @DisplayName("트랜잭션 커밋 전에는 등록하지 않고 rollback 요청은 생성하지 않는다")
    void waitsForCommitAndDropsRollback() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            generator.requestSummary(1L, null);
            assertThat(tasks).isEmpty();
            List<TransactionSynchronization> hooks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(hooks).hasSize(1);
            hooks.forEach(TransactionSynchronization::afterCommit);
            assertThat(tasks).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        tasks.clear();
        TransactionSynchronizationManager.initSynchronization();
        try {
            generator.requestSummary(1L, null);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(hook -> hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(tasks).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("일시적 오류는 2초·4초 대기로 최대 두 번 재시도한다")
    void retriesAtMostTwiceAndSavesThirdSuccess() {
        when(client.generate(any())).thenThrow(new ArticleSummaryClient.Failure(true))
                .thenThrow(new ArticleSummaryClient.Failure(true))
                .thenReturn(new ArticleSummaryClient.Result(SummaryTestFixtures.CONTENT, 150));
        run();
        verify(client, times(3)).generate(any());
        verify(guard, times(3)).reserve(2000);
        assertThat(waits).containsExactly(2000L, 4000L);
        verify(repository).updateSummary(eq(1L), anyString(), any(), eq(3L));
        verify(guard, never()).cooldown(anyLong(), anyString());
    }

    @Test
    @DisplayName("세 번 실패하면 저장하지 않고 60초 cooldown 에 맡긴다")
    void exhaustedRetriesDoNotSaveFailure() {
        when(client.generate(any())).thenThrow(new ArticleSummaryClient.Failure(true));
        run();
        verify(client, times(3)).generate(any());
        verify(repository, never()).updateSummary(anyLong(), anyString(), any(), anyLong());
        verify(guard).cooldown(1L, "owner");
        verify(guard).release(1L, "owner");
    }

    @Test
    @DisplayName("인증 오류는 한 번만 호출한다")
    void doesNotRetryAuthenticationFailure() {
        when(client.generate(any())).thenThrow(new ArticleSummaryClient.Failure(false));
        run();
        verify(client).generate(any());
        assertThat(waits).isEmpty();
        verify(guard).cooldown(1L, "owner");
    }

    @Test
    @DisplayName("잘못된 형식도 두 번까지만 재시도하고 usage 는 매번 기록한다")
    void invalidOutputUsesSameRetryBudget() {
        when(client.generate(any())).thenReturn(new ArticleSummaryClient.Result("{\"lines\":[]}", 50));
        run();
        verify(client, times(3)).generate(any());
        verify(guard, times(3)).recordUsage("reservation", 50);
        verify(repository, never()).updateSummary(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("Redis 한도 부족은 AI 를 호출하지 않고 cooldown 후 다음 상세 요청으로 넘긴다")
    void quotaDeniedDoesNotCallAi() {
        when(guard.reserve(anyLong())).thenReturn(null);
        run();
        verify(client, never()).generate(any());
        verify(guard).cooldown(1L, "owner");
    }

    @Test
    @DisplayName("중복 lock·이미 생성된 캐시·Redis 장애는 AI 호출 없이 종료한다")
    void duplicateCachedAndUnavailableRedisSkipAi() {
        when(guard.acquire(1L)).thenReturn(null);
        run();
        verifyNoInteractions(repository);
        when(guard.acquire(1L)).thenReturn("owner");
        when(article.getSummary()).thenReturn("기존 요약");
        run();
        when(guard.acquire(1L)).thenThrow(new IllegalStateException("redis unavailable"));
        run();
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("키가 없거나 executor 가 포화되어도 요청자는 실패하지 않는다")
    void disabledOrFullExecutorIsSafe() {
        when(client.isEnabled()).thenReturn(false);
        generator.requestSummary(1L, null);
        assertThat(tasks).isEmpty();
        when(client.isEnabled()).thenReturn(true);
        AsyncArticleSummaryGenerator rejected = new AsyncArticleSummaryGenerator(
                repository, client, guard, task -> { throw new java.util.concurrent.RejectedExecutionException(); },
                transactions, SummaryTestFixtures.properties(), new ArticleSummaryValidator());
        rejected.requestSummary(1L, null);
        verify(client, never()).generate(any());
    }

    @Test
    @DisplayName("생성 중 lock 을 잃거나 원문 저장이 0건이면 재계산 루프 없이 결과를 폐기한다")
    void lostOwnershipAndStaleWritesAreDiscarded() {
        when(guard.owns(1L, "owner")).thenReturn(true, false);
        run();
        verify(repository, never()).updateSummary(anyLong(), anyString(), any(), anyLong());
        when(guard.owns(1L, "owner")).thenReturn(true);
        when(repository.updateSummary(anyLong(), anyString(), any(), anyLong())).thenReturn(0);
        run();
        verify(client, times(2)).generate(any());
        assertThat(tasks).isEmpty();
        verify(guard, never()).cooldown(anyLong(), anyString());
    }

    @Test
    @DisplayName("저장 timeout 은 작업의 남은 초로 줄이고 1초 미만이면 저장하지 않는다")
    void boundsWriteToRemainingDeadline() {
        java.util.concurrent.atomic.AtomicLong elapsed = new java.util.concurrent.atomic.AtomicLong();
        AsyncArticleSummaryGenerator bounded = new AsyncArticleSummaryGenerator(
                repository, client, guard, tasks::add, transactions, SummaryTestFixtures.properties(),
                new ArticleSummaryValidator(), waits::add, elapsed::get);
        when(client.generate(any())).thenAnswer(call -> {
            elapsed.set(java.time.Duration.ofSeconds(66).toNanos());
            return new ArticleSummaryClient.Result(SummaryTestFixtures.CONTENT, 150);
        });
        bounded.requestSummary(1L, null);
        tasks.removeFirst().run();
        org.mockito.ArgumentCaptor<org.springframework.transaction.TransactionDefinition> definitions =
                org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues().getLast().getTimeout()).isEqualTo(4);

        clearInvocations(repository);
        when(client.generate(any())).thenAnswer(call -> {
            elapsed.set(java.time.Duration.ofMillis(69500).toNanos());
            return new ArticleSummaryClient.Result(SummaryTestFixtures.CONTENT, 150);
        });
        elapsed.set(0);
        bounded.requestSummary(1L, null);
        tasks.removeFirst().run();
        verify(repository, never()).updateSummary(anyLong(), anyString(), any(), anyLong());
    }
}
