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
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
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
     * @param interestOnly 안 보내면 <b>켜짐</b>입니다. 그래서 {@code Boolean} 입니다 —
     *                     {@code boolean} 이면 "안 보냄" 과 "false" 가 구분되지 않아 토글을 끌 수 없습니다.
     * @param sort         {@code published_at,desc} 형태. 허용 기준은 화이트리스트이고
     *                     서버가 항상 {@code id DESC} 를 뒤에 붙입니다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ArticleSummaryResponse>> search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "source_type", required = false) SourceType sourceType,
            @RequestParam(name = "category_id", required = false) List<Long> categoryIds,
            @RequestParam(name = "vendor_id", required = false) List<Long> vendorIds,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "interest_only", required = false) Boolean interestOnly,
            @RequestParam(name = "starts_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsFrom,
            @RequestParam(name = "ends_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsTo,
            @RequestParam(name = "has_deadline", required = false) Boolean hasDeadline,
            @PageableDefault(size = 20) Pageable pageable) {

        ArticleSearchCondition condition = new ArticleSearchCondition(
                sourceType, categoryIds, vendorIds, keyword, interestOnly, startsFrom, endsTo, hasDeadline);

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
}
