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
            @RequestParam(name = "vendor_id", required = false) Integer vendorId
    ) {
        ClubArticleListResponse response = clubArticleService.getClubArticles(page, size, vendorId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{articleId}")
    public ApiResponse<ClubArticleDetailResponse> getClubArticleDetail(
            @PathVariable Integer articleId
    ) {
        ClubArticleDetailResponse response = clubArticleService.getClubArticleDetail(articleId);
        return ApiResponse.success(response);
    }
}
