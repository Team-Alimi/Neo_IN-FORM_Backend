package today.inform.inform.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import today.inform.inform.comment.entity.Comment;

/**
 * CMT-01.
 *
 * <p><b>답글은 없습니다.</b> 댓글은 공지 하나에 대한 평면 목록이고 대댓글을 달 수 없습니다.
 * 예전에 있던 {@code parentId} 는 제거됐습니다 — 보내도 무시됩니다.
 *
 * <p>길이·공백 검증이 여기와 {@link Comment} 양쪽에 있습니다. 중복이 아니라 역할이 다릅니다.
 * 여기서 막으면 어느 필드가 문제인지 알려 줄 수 있고, 엔티티 쪽은 이 DTO 를 거치지 않는
 * 다른 경로(배치·관리도구)에서도 규칙이 지켜지게 합니다.
 */
public record CreateCommentRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = Comment.MAX_CONTENT_LENGTH, message = "댓글은 1000자를 넘을 수 없습니다.")
        String content) {
}
