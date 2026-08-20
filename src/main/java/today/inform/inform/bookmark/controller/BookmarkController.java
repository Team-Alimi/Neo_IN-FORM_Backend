package today.inform.inform.bookmark.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.bookmark.service.BookmarkService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * BMK-01 ~ BMK-04. 전부 로그인이 필요합니다.
 *
 * <p><b>경로에 공지 번호가 들어가는데 왜 POST 가 아니라 PUT 인가</b>
 * "북마크를 추가한다" 가 아니라 "이 공지의 북마크 상태를 켬으로 만든다" 이기 때문입니다.
 * 같은 요청을 두 번 보내도 결과가 같아야 하고, 그게 PUT 의 정의입니다.
 * POST 로 두면 클라이언트가 재시도할 때마다 "이미 있음" 오류를 처리해야 합니다.
 */
@RestController
@RequestMapping("/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    /**
     * BMK-02 목록. 피드와 같은 필터를 받습니다.
     *
     * <p>{@code interest_only} 는 받지 않습니다 — 내가 저장한 목록을
     * 관심 분야로 다시 거르는 건 기대하지 않는 동작입니다.
     */
    @GetMapping
    public ApiResponse<PageResponse<ArticleSummaryResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "source_type", required = false) SourceType sourceType,
            @RequestParam(name = "category_id", required = false) List<Long> categoryIds,
            @RequestParam(name = "vendor_id", required = false) List<Long> vendorIds,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "starts_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsFrom,
            @RequestParam(name = "ends_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsTo,
            @RequestParam(name = "has_deadline", required = false) Boolean hasDeadline,
            @PageableDefault(size = 20) Pageable pageable) {

        ArticleSearchCondition condition = new ArticleSearchCondition(
                sourceType, categoryIds, vendorIds, keyword, false, startsFrom, endsTo, hasDeadline);

        return ApiResponse.success(
                PageResponse.from(bookmarkService.list(principal.userId(), condition, pageable)));
    }

    /** BMK-01 추가. 멱등. */
    @PutMapping("/articles/{articleId}")
    public ApiResponse<Void> add(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable Long articleId) {
        bookmarkService.add(principal.userId(), articleId);
        return ApiResponse.success(null);
    }

    /** BMK-03 해제. 멱등. */
    @DeleteMapping("/articles/{articleId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long articleId) {
        bookmarkService.remove(principal.userId(), articleId);
        return ApiResponse.success(null);
    }

    /**
     * BMK-04 전체 삭제.
     *
     * <p>지운 개수를 돌려줍니다. 되돌릴 수 없는 조작이라 화면에서
     * "12개를 삭제했습니다" 로 확인시켜 줄 수 있어야 합니다.
     */
    @DeleteMapping
    public ApiResponse<Map<String, Integer>> removeAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "source_type", required = false) SourceType sourceType) {
        int removed = bookmarkService.removeAll(principal.userId(), sourceType);
        return ApiResponse.success(Map.of("removed_count", removed));
    }
}
