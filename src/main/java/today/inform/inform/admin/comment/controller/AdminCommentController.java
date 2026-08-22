package today.inform.inform.admin.comment.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.comment.dto.request.AdminCommentSearchCondition;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.comment.dto.request.CommentIdsRequest;
import today.inform.inform.admin.comment.dto.response.AdminCommentSummary;
import today.inform.inform.admin.comment.service.AdminCommentService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;

/** ADM-17 댓글 관리. {@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다. */
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    /**
     * 댓글 목록·검색. 공지를 가로질러 찾습니다.
     *
     * @param keyword        본문 부분 일치. 2글자 미만은 무시합니다
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminCommentSummary>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "article_id", required = false) Long articleId,
            @RequestParam(name = "user_id", required = false) Long userId,
            @PageableDefault(size = 20) Pageable pageable) {

        AdminCommentSearchCondition condition =
                new AdminCommentSearchCondition(keyword, articleId, userId);

        return ApiResponse.success(
                PageResponse.from(adminCommentService.search(condition, pageable)));
    }

    /**
     * 일괄 삭제.
     *
     * <p>공지 벌크와 <b>같은 응답 형태</b>입니다 — 이미 지워진 건은 {@code failed} 에 사유와 함께 담깁니다.
     * 조용히 빼면 관리자는 30건을 골랐는데 28건만 처리된 것을 모릅니다.
     */
    @PostMapping("/bulk/delete")
    public ApiResponse<BulkResult> deleteAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CommentIdsRequest request) {
        return ApiResponse.success(adminCommentService.deleteAll(request.ids(), principal.userId()));
    }
}
