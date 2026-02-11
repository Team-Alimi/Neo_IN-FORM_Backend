package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.service.SchoolArticleService;

@RestController
@RequestMapping("/api/v1/school_articles")
@RequiredArgsConstructor
public class SchoolArticleController {

    private final SchoolArticleService schoolArticleService;

    @GetMapping
    public ApiResponse<SchoolArticleListResponse> getSchoolArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer category_id,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(schoolArticleService.getSchoolArticles(page, size, category_id, keyword, userId));
    }

    @GetMapping("/{articleId}")
    public ApiResponse<SchoolArticleDetailResponse> getSchoolArticleDetail(
            @PathVariable Integer articleId,
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(schoolArticleService.getSchoolArticleDetail(articleId, userId));
    }
}
