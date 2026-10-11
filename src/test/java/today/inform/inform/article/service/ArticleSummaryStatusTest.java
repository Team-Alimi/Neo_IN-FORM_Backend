package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryStatusResponse;
import today.inform.inform.article.dto.response.SummaryStatus;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.user.entity.UserRole;

/**
 * {@code summary_status} 와 요약 전용 조회.
 *
 * <p><b>이 파일이 지키는 것은 두 가지입니다.</b>
 * <ul>
 *   <li>요약이 비어 있는 <b>이유</b>가 응답에 드러난다 — 안 그러면 화면은 "요약 없는 공지" 와
 *       "지금 생성 중" 을 구분할 수 없어 "생성 중" 안내를 띄울 수 없습니다.</li>
 *   <li>요약 전용 조회({@code getSummary})가 <b>생성을 걸지 않는다</b> — 이게 깨지면
 *       폴링이 작업 큐(용량 20, 초과 시 거부)를 채워 실제 생성 작업을 밀어냅니다.</li>
 * </ul>
 *
 * <p>Docker 없이 도는 단위 테스트입니다. 실제 생성 동작은 {@code AsyncArticleSummaryGeneratorTest}
 * 가 봅니다 — 여기서는 "상태를 어떻게 알려 주는가" 만 봅니다.
 */
class ArticleSummaryStatusTest {

    private final ArticleQueryRepository queries = mock(ArticleQueryRepository.class);
    private final ArticleQueryValidator validator = mock(ArticleQueryValidator.class);
    private final ArticleViewCounter views = mock(ArticleViewCounter.class);
    private final ArticleSummaryGenerator generator = mock(ArticleSummaryGenerator.class);
    private final ArticleSummaryClient client = mock(ArticleSummaryClient.class);
    private final ArticleService service =
            new ArticleService(queries, validator, views, generator, client);
    private final AuthPrincipal principal = new AuthPrincipal(7L, UserRole.USER);

    // ─────────────────────────────────────────────────────────────────────────
    // 상세 조회의 summary_status
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("요약이 있으면 READY 이고 생성기를 부르지 않는다")
    void readyWhenSummaryExists() {
        when(queries.findDetail(1L, 7L)).thenReturn(Optional.of(detail("요약 있음")));

        assertThat(service.getDetail(1L, principal).summaryStatus())
                .isEqualTo(SummaryStatus.READY);
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("★ 요약이 없고 기능이 켜져 있으면 PENDING 이다 — 화면이 \"생성 중\" 을 띄울 근거")
    void pendingWhenGenerationStarts() {
        when(queries.findDetail(1L, 7L)).thenReturn(Optional.of(detail(null)));
        when(client.isEnabled()).thenReturn(true);

        assertThat(service.getDetail(1L, principal).summaryStatus())
                .isEqualTo(SummaryStatus.PENDING);
        verify(generator).requestSummary(1L, null);
    }

    @Test
    @DisplayName("★ 기능이 꺼져 있으면 NONE 이고 생성 요청조차 하지 않는다 — 전원이 헛 폴링하는 것을 막는다")
    void noneWhenDisabled() {
        when(queries.findDetail(1L, 7L)).thenReturn(Optional.of(detail(null)));
        when(client.isEnabled()).thenReturn(false);

        assertThat(service.getDetail(1L, principal).summaryStatus())
                .as("PENDING 을 내보내면 모든 사용자가 모든 공지에서 재조회를 반복합니다")
                .isEqualTo(SummaryStatus.NONE);
        verify(generator, never()).requestSummary(anyLong(), any());
    }

    @Test
    @DisplayName("작업 등록이 거부되면 NONE 이다 — 기다려도 안 생긴다")
    void noneWhenSubmissionRejected() {
        when(queries.findDetail(1L, 7L)).thenReturn(Optional.of(detail(null)));
        when(client.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("queue full"))
                .when(generator).requestSummary(anyLong(), any());

        assertThat(service.getDetail(1L, principal).summaryStatus())
                .isEqualTo(SummaryStatus.NONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 요약 전용 조회 (폴링 경로)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 요약 전용 조회는 생성을 걸지 않는다 — 폴링이 작업 큐를 채우면 실제 생성이 밀려난다")
    void summaryLookupNeverTriggersGeneration() {
        when(queries.findSummaryRow(1L)).thenReturn(Optional.of(row(null)));
        when(client.isEnabled()).thenReturn(true);

        ArticleSummaryStatusResponse response = service.getSummary(1L);

        assertThat(response.summaryStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(response.summary()).isNull();
        verifyNoInteractions(generator);
        verifyNoInteractions(views);
    }

    @Test
    @DisplayName("요약이 채워졌으면 READY 와 함께 본문을 돌려준다")
    void summaryLookupReturnsReady() {
        when(queries.findSummaryRow(1L)).thenReturn(Optional.of(row("① ... ② ... ③ ...")));

        ArticleSummaryStatusResponse response = service.getSummary(1L);

        assertThat(response.summaryStatus()).isEqualTo(SummaryStatus.READY);
        assertThat(response.summary()).isEqualTo("① ... ② ... ③ ...");
    }

    @Test
    @DisplayName("기능이 꺼져 있으면 NONE 이다 — 화면이 재조회를 멈출 신호")
    void summaryLookupReturnsNoneWhenDisabled() {
        when(queries.findSummaryRow(1L)).thenReturn(Optional.of(row(null)));
        when(client.isEnabled()).thenReturn(false);

        assertThat(service.getSummary(1L).summaryStatus()).isEqualTo(SummaryStatus.NONE);
    }

    @Test
    @DisplayName("★ 볼 수 없는 공지는 404 다 — 상세로 404 인 공지의 요약이 이쪽으로 새면 안 된다")
    void summaryLookupHidesInvisibleArticle() {
        when(queries.findSummaryRow(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTICLE_NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static ArticleQueryRepository.SummaryRow row(String summary) {
        return new ArticleQueryRepository.SummaryRow(summary);
    }

    private static ArticleDetailResponse detail(String summary) {
        return new ArticleDetailResponse(1L, null, "제목", "본문", summary,
                null, null, null, null, null, 0, 0, 0, false, false,
                List.of(), List.of(), List.of());
    }
}
