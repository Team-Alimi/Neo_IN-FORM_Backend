package today.inform.inform.admin.comment.dto.response;

import java.time.OffsetDateTime;

/**
 * 관리자 댓글 목록 한 줄.
 *
 * <p><b>작성자를 가리지 않습니다.</b> 사용자용 목록은 탈퇴자를 "탈퇴한 사용자" 로 감추지만,
 * 여기는 신고·문의에 대응해 <b>같은 사람이 쓴 다른 댓글을 찾아야</b> 하는 화면입니다.
 * 가리면 기능이 성립하지 않습니다. {@code /admin/**} 은 관리자 전용입니다.
 *
 * @param content   삭제된 댓글이면 빈 문자열입니다. 지울 때 본문까지 비우기 때문에
 *                  <b>관리자도 내용을 되살릴 수 없습니다</b>({@code Comment#softDelete})
 * @param isReply   답글이면 {@code true}. 원댓글을 지우면 자리만 남고,
 *                  답글을 지우면 행이 사라지는 차이가 있어 화면이 미리 알려 줘야 합니다
 * @param replyCount 이 댓글에 달린 답글 수. 0 이 아니면 삭제해도 자리가 남습니다
 */
public record AdminCommentSummary(
        Long id,
        Long articleId,
        String articleTitle,
        Long authorId,
        String authorEmail,
        String authorName,
        boolean authorWithdrawn,
        String content,
        OffsetDateTime createdAt) {
}
