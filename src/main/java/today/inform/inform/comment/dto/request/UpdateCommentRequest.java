package today.inform.inform.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import today.inform.inform.comment.entity.Comment;

/** CMT-03. 내용만 바꿉니다 — 상위 댓글이나 대상 공지는 옮길 수 없습니다. */
public record UpdateCommentRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = Comment.MAX_CONTENT_LENGTH, message = "댓글은 1000자를 넘을 수 없습니다.")
        String content) {
}
