package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
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
    public SchoolArticleListResponse getSchoolArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer category_id,
            @RequestParam(required = false) String keyword
    ) {
        return schoolArticleService.getSchoolArticles(page, size, category_id, keyword);
    }

    @GetMapping("/{articleId}")
    public ApiResponse<SchoolArticleDetailResponse> getSchoolArticleDetail(@PathVariable Integer articleId) {
        return ApiResponse.success(schoolArticleService.getSchoolArticleDetail(articleId));
    }
}
