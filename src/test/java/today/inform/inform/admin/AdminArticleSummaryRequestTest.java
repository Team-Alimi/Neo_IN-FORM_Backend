package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import today.inform.inform.admin.article.repository.AdminArticleQueryRepository;
import today.inform.inform.admin.article.service.AdminArticleService;
import today.inform.inform.admin.article.service.BulkExecutor;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.article.service.ArticleSummaryGenerator;
import today.inform.inform.global.support.AuditMemo;

class AdminArticleSummaryRequestTest {

    @Test
    @DisplayName("재생성 요청 등록 실패는 요약 초기화 트랜잭션을 실패시키지 않는다")
    void submissionFailureDoesNotRollBackInvalidation() {
        ArticleRepository repository = mock(ArticleRepository.class);
        ArticleSummaryGenerator generator = mock(ArticleSummaryGenerator.class);
        EntityManager em = mock(EntityManager.class);
        Query update = mock(Query.class);
        AdminArticleService service = new AdminArticleService(mock(AdminArticleQueryRepository.class),
                repository, generator, mock(BulkExecutor.class), mock(AuditMemo.class));
        ReflectionTestUtils.setField(service, "em", em);
        when(repository.existsById(1L)).thenReturn(true);
        when(em.createNativeQuery(anyString())).thenReturn(update);
        when(update.setParameter("id", 1L)).thenReturn(update);
        doThrow(new IllegalStateException("submission failed")).when(generator).requestSummary(anyLong(), any());

        assertThatCode(() -> service.regenerateSummary(1L)).doesNotThrowAnyException();

        var order = inOrder(update, generator);
        order.verify(update).executeUpdate();
        order.verify(generator).requestSummary(1L, null);
    }
}
