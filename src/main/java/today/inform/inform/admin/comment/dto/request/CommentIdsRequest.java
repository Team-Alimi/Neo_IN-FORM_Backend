package today.inform.inform.admin.comment.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * ADM-17 댓글 일괄 삭제.
 *
 * <p>상한을 두는 이유는 잠금입니다. 삭제 판정은 댓글마다 행을 잠그고 시작하는데
 * ({@code CommentRepository#lockById}), 한 요청이 수천 건을 붙들면 그동안 그 글타래의
 * 답글 작성이 전부 대기합니다.
 */
public record CommentIdsRequest(
        @NotEmpty(message = "삭제할 댓글을 선택해 주세요.")
        @Size(max = 200, message = "한 번에 200건까지 처리할 수 있습니다.")
        List<@NotNull(message = "댓글 번호가 비어 있습니다.") Long> commentIds) {
}
