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
 *
 * <p>{@code isReply}·{@code replyCount} 는 없습니다. 답글 기능과 함께 제거됐고
 * 되살리지 않기로 결정됐습니다(2026-10-12) — 모든 댓글이 원댓글입니다.
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
