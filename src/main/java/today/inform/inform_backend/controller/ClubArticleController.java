package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.ClubArticleDetailResponse;
import today.inform.inform_backend.dto.ClubArticleListResponse;
import today.inform.inform_backend.service.ClubArticleService;

@RestController
@RequestMapping("/api/v1/club_articles")
@RequiredArgsConstructor
public class ClubArticleController {

    private final ClubArticleService clubArticleService;

    @GetMapping
    public ApiResponse<ClubArticleListResponse> getClubArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "4") Integer size,
            @RequestParam(name = "vendor_id", required = false) Integer vendorId,
            @RequestParam(required = false) String keyword
    ) {
        ClubArticleListResponse response = clubArticleService.getClubArticles(page, size, vendorId, keyword);
        return ApiResponse.success(response);
    }

    @GetMapping("/{article_id}")
    public ApiResponse<ClubArticleDetailResponse> getClubArticleDetail(
            @PathVariable(name = "article_id") Integer articleId
    ) {
        ClubArticleDetailResponse response = clubArticleService.getClubArticleDetail(articleId);
        return ApiResponse.success(response);
    }
}
