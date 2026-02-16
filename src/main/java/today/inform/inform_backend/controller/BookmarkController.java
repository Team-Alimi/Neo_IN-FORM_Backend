package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.BookmarkRequest;
import today.inform.inform_backend.dto.ClubArticleListResponse;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.service.BookmarkService;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping("/school")
    public ApiResponse<SchoolArticleListResponse> getBookmarkedSchoolArticles(
            @AuthenticationPrincipal Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(name = "category_id", required = false) List<Integer> categoryIds,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(bookmarkService.getBookmarkedSchoolArticles(userId, categoryIds, keyword, page, size));
    }

    @DeleteMapping("/school/all")
    public ApiResponse<Void> deleteAllBookmarkedSchoolArticles(
            @AuthenticationPrincipal Integer userId
    ) {
        bookmarkService.deleteAllBookmarkedSchoolArticles(userId);
        return ApiResponse.success(null);
    }

    @PostMapping
    public ApiResponse<Boolean> toggleBookmark(
            @AuthenticationPrincipal Integer userId,
            @RequestBody BookmarkRequest request
    ) {
        boolean isBookmarked = bookmarkService.toggleBookmark(userId, request.getArticleType(), request.getArticleId());
        return ApiResponse.success(isBookmarked);
    }
}
