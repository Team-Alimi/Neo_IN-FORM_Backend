package today.inform.inform.article.controller;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.DeadlineStatus;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.dto.response.ArticleSummaryStatusResponse;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.service.ArticleService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * 공지 조회. 전부 로그인이 필요합니다.
 *
 * <p>경로에 {@code /api/v1} 을 쓰지 않습니다 — {@code WebConfig} 가 모든 RestController 에 붙입니다.
 *
 * <p><b>쿼리 파라미터를 하나씩 받는 이유</b>
 * 응답 본문은 Jackson 이 snake_case 로 바꿔 주지만 <b>쿼리 파라미터에는 적용되지 않습니다.</b>
 * 객체 바인딩({@code @ModelAttribute})으로 두면 {@code sourceType} 을 받게 되어
 * 명세({@code source_type})와 어긋납니다. 이름을 명시적으로 적습니다.
 */
@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /**
     * ART-01 / 03 / 04 목록.
     *
     * @param interestOnly 관심 분류로 거를지. <b>안 보내면 꺼짐</b>입니다 —
     *                     이유는 {@link ArticleSearchCondition#isInterestOnly()} 주석에 있습니다.
     *                     "안 보냄" 과 "false" 가 같은 뜻이므로 삼상태가 필요 없습니다.
     * @param sort         {@code published_at,desc} 형태. 허용 기준은 화이트리스트이고
     *                     서버가 항상 {@code id DESC} 를 뒤에 붙입니다.
     * @param deadlineStatuses 마감 상태로 거릅니다. 여러 개를 쉼표로 보낼 수 있습니다
     *                     ({@code ?deadline_status=OPEN,CLOSING_SOON}).
     *                     값은 응답의 {@code deadline_status} 와 같은 문자열이라,
     *                     화면이 카드에 붙인 배지 값을 그대로 필터로 넘길 수 있습니다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ArticleSummaryResponse>> search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "source_type", required = false) SourceType sourceType,
            @RequestParam(name = "category_id", required = false) List<Long> categoryIds,
            @RequestParam(name = "vendor_id", required = false) List<Long> vendorIds,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "interest_only", defaultValue = "false") boolean interestOnly,
            @RequestParam(name = "starts_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsFrom,
            @RequestParam(name = "ends_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsTo,
            @RequestParam(name = "has_deadline", required = false) Boolean hasDeadline,
            @RequestParam(name = "deadline_status", required = false) List<DeadlineStatus> deadlineStatuses,
            @PageableDefault(size = 20) Pageable pageable) {

        ArticleSearchCondition condition = new ArticleSearchCondition(
                sourceType, categoryIds, vendorIds, keyword, interestOnly,
                startsFrom, endsTo, hasDeadline, deadlineStatuses);

        return ApiResponse.success(
                PageResponse.from(articleService.search(condition, pageable, principal)));
    }

    /** ART-05 인기 공지. 목록과 달리 페이징이 없습니다 — 홈 화면의 고정 영역입니다. */
    @GetMapping("/popular")
    public ApiResponse<List<ArticleSummaryResponse>> popular(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return ApiResponse.success(articleService.getPopular(principal, limit));
    }

    /** ART-02 / 06 / 07 상세. */
    @GetMapping("/{articleId}")
    public ApiResponse<ArticleDetailResponse> detail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long articleId) {
        return ApiResponse.success(articleService.getDetail(articleId, principal));
    }

    /**
     * ART-07 AI 요약만 조회. <b>"생성 중" 을 띄운 화면이 재조회할 때 쓰는 경로입니다.</b>
     *
     * <p>상세 응답의 {@code summary_status} 가 {@code PENDING} 일 때만 부르세요.
     * {@code NONE} 이면 기다려도 안 생깁니다.
     *
     * <p><b>상세 조회로 폴링하면 안 됩니다.</b> 그쪽은 요약이 빌 때마다 생성 요청을
     * 다시 던져서 작업 큐를 폴링으로 채우고, 분류·출처·첨부까지 조인한 큰 응답을 돌려줍니다.
     * 이 경로는 읽기만 하고 조회수도 세지 않습니다.
     *
     * <p><b>무한 폴링은 금지입니다.</b> {@code PENDING} 은 "요청이 걸렸다" 까지만 뜻하고,
     * 생성이 실패했거나 쿨다운 중인 경우도 같은 값으로 나옵니다.
     * 2초 간격 4회 정도면 성공 케이스는 거의 잡히고, 그 뒤에는 요약 박스를 접으세요.
     * 사용자가 나중에 다시 들어오면 그때는 채워져 있을 가능성이 높습니다.
     */
    @GetMapping("/{articleId}/summary")
    public ApiResponse<ArticleSummaryStatusResponse> summary(@PathVariable Long articleId) {
        return ApiResponse.success(articleService.getSummary(articleId));
    }
}
