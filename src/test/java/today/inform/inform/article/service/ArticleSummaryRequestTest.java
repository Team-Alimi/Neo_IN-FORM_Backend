package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.SummaryStatus;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.user.entity.UserRole;

class ArticleSummaryRequestTest {
    private final ArticleQueryRepository queries = mock(ArticleQueryRepository.class);
    private final ArticleQueryValidator validator = mock(ArticleQueryValidator.class);
    private final ArticleViewCounter views = mock(ArticleViewCounter.class);
    private final ArticleSummaryGenerator generator = mock(ArticleSummaryGenerator.class);
    private final ArticleSummaryClient client = mock(ArticleSummaryClient.class);
    private final ArticleService service =
            new ArticleService(queries, validator, views, generator, client);
    private final AuthPrincipal principal = new AuthPrincipal(7L, UserRole.USER);

    @Test
    @DisplayName("요약 요청이 실패해도 상세 응답은 그대로 반환한다")
    void failedSubmissionDoesNotFailDetail() {
        ArticleDetailResponse detail = detail(null);
        when(queries.findDetail(1L, 7L)).thenReturn(Optional.of(detail));
        when(client.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("submission failed"))
                .when(generator).requestSummary(anyLong(), any());

        // summary_status 가 생기면서 응답이 사본으로 바뀝니다(record 는 불변).
        // 그래서 동일 객체(isSameAs)가 아니라 내용 보존으로 판정합니다.
        ArticleDetailResponse result = service.getDetail(1L, principal);
        assertThat(result.title()).isEqualTo(detail.title());
        assertThat(result.content()).isEqualTo(detail.content());
        assertThat(result.summary()).isNull();
        assertThat(result.summaryStatus())
                .as("등록 자체가 거부됐으면 기다려도 안 생기므로 재조회하지 말라고 알려야 합니다")
                .isEqualTo(SummaryStatus.NONE);
    }

    @Test
    @DisplayName("기존 요약은 재사용하며 생성기를 호출하지 않는다")
    void cachedSummaryIsReused() {
        ArticleDetailResponse detail = detail("기존 요약");
        when(queries.findDetail(1L, 7L)).thenReturn(Optional.of(detail));
        ArticleDetailResponse result = service.getDetail(1L, principal);
        assertThat(result.summary()).isEqualTo("기존 요약");
        assertThat(result.summaryStatus()).isEqualTo(SummaryStatus.READY);
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("목록과 인기 조회는 요약 생성을 요청하지 않는다")
    void listsDoNotGenerate() {
        ArticleSearchCondition condition = mock(ArticleSearchCondition.class);
        PageRequest page = PageRequest.of(0, 10);
        when(queries.search(condition, 7L, page)).thenReturn(Page.empty());
        when(queries.findPopular(7L, 10)).thenReturn(List.of());
        service.search(condition, page, principal);
        service.getPopular(principal, 10);
        verifyNoInteractions(generator);
    }

    private ArticleDetailResponse detail(String summary) {
        return new ArticleDetailResponse(1L, null, "제목", "본문", summary,
                null, null, null, null, null, 0, 0, 0, false, false,
                List.of(), List.of(), List.of());
    }
}
