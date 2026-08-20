package today.inform.inform.like.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.like.service.LikeService;

/**
 * LIK-01 좋아요 토글.
 *
 * <p>이전 문서에서 "추천" 이라 부르던 기능입니다. 동아리 추천(REC)과 용어가 겹쳐
 * 좋아요로 바뀌었고 경로도 {@code /likes} 입니다.
 */
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PutMapping("/articles/{articleId}")
    public ApiResponse<Void> like(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable Long articleId) {
        likeService.like(principal.userId(), articleId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/articles/{articleId}")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long articleId) {
        likeService.unlike(principal.userId(), articleId);
        return ApiResponse.success(null);
    }
}
