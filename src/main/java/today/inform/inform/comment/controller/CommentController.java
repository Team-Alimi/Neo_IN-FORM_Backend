package today.inform.inform.comment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.comment.dto.request.CreateCommentRequest;
import today.inform.inform.comment.dto.request.UpdateCommentRequest;
import today.inform.inform.comment.dto.response.CommentResponse;
import today.inform.inform.comment.service.CommentService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * CMT-01 ~ CMT-04. 전부 로그인이 필요합니다.
 *
 * <p><b>경로 접두사가 두 가지입니다.</b> 목록·작성은 공지에 속하므로 {@code /articles/{id}/comments},
 * 수정·삭제는 댓글 id 하나로 대상이 정해지므로 {@code /comments/{id}} 입니다.
 * 후자를 굳이 공지 아래에 두면 클라이언트가 쓰지도 않을 공지 번호를 함께 들고 다녀야 하고,
 * 그 번호가 실제 댓글의 공지와 다를 때 무엇을 해야 하는지가 또 문제가 됩니다.
 *
 * <p>클래스 레벨 {@code @RequestMapping} 을 두지 않은 이유가 그것입니다.
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** CMT-02 목록. 원댓글 시간순 페이징 + 답글 중첩. */
    @GetMapping("/articles/{articleId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long articleId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(
                commentService.list(articleId, principal.userId(), pageable)));
    }

    /**
     * CMT-01 작성.
     *
     * <p>{@code parent_id} 를 주면 답글입니다. <b>답글에 답글은 달 수 없고</b>
     * 그 판정은 DB 트리거가 합니다 — 위반하면 {@code COMMENT_DEPTH_EXCEEDED}(400).
     */
    @PostMapping("/articles/{articleId}/comments")
    public ApiResponse<CommentResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long articleId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(commentService.create(
                articleId, principal.userId(), request.content()));
    }

    /** CMT-03 수정. 본인만. */
    @PatchMapping("/comments/{commentId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        commentService.update(commentId, principal.userId(), request.content());
        return ApiResponse.success(null);
    }

    /** CMT-04 삭제. 본인만. 답글이 있으면 자리를 남기고, 없으면 지웁니다. */
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long commentId) {
        commentService.delete(commentId, principal.userId());
        return ApiResponse.success(null);
    }
}
