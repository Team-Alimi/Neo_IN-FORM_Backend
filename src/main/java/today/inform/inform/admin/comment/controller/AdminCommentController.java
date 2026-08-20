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
     * @param includeDeleted 기본 {@code false}. 삭제된 댓글은 본문이 비어 있어 검색으로는 못 찾습니다
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminCommentSummary>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "article_id", required = false) Long articleId,
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "include_deleted", defaultValue = "false") boolean includeDeleted,
            @PageableDefault(size = 20) Pageable pageable) {

        AdminCommentSearchCondition condition =
                new AdminCommentSearchCondition(keyword, articleId, userId, includeDeleted);

        return ApiResponse.success(
                PageResponse.from(adminCommentService.search(condition, pageable)));
    }

    /**
     * 일괄 삭제.
     *
     * <p>이미 지워진 건은 조용히 건너뛰므로 요청한 개수와 응답의 {@code deleted} 가 다를 수 있습니다.
     * 화면은 이 숫자를 그대로 보여 주고 목록을 새로 고쳐야 합니다.
     */
    @PostMapping("/bulk/delete")
    public ApiResponse<Map<String, Integer>> deleteAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CommentIdsRequest request) {
        int deleted = adminCommentService.deleteAll(request.commentIds(), principal.userId());
        return ApiResponse.success(Map.of("deleted", deleted));
    }
}
