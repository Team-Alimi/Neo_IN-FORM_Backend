package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.BookmarkRequest;
import today.inform.inform_backend.service.BookmarkService;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ApiResponse<Boolean> toggleBookmark(
            @AuthenticationPrincipal Integer userId,
            @RequestBody BookmarkRequest request
    ) {
        boolean isBookmarked = bookmarkService.toggleBookmark(userId, request.getArticle_type(), request.getArticle_id());
        return ApiResponse.success(isBookmarked);
    }
}
