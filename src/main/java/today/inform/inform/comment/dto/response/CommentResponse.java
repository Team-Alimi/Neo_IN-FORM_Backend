package today.inform.inform.comment.dto.response;

import java.time.OffsetDateTime;
import today.inform.inform.comment.repository.CommentRow;
import today.inform.inform.user.entity.UserStatus;

/**
 * 댓글 한 건. 답글은 {@code replies} 에 중첩되며 <b>답글의 답글은 없습니다</b>(1단계 제한).
 *
 * @param content 삭제된 댓글이면 {@code null} 입니다. 프론트가 "삭제된 댓글입니다" 를 그립니다.
 *                서버가 그 문구를 내려보내지 않는 이유는 문구가 화면 언어·디자인에 속하기 때문입니다.
 * @param author  삭제된 댓글이면 {@code null} 입니다 — 지운 댓글의 작성자까지 남길 이유가 없습니다.
 * @param isMine  수정·삭제 버튼 노출용. 권한 판정 자체는 서버가 다시 합니다.
 */
public record CommentResponse(
        Long id,
        String content,
        Author author,
        OffsetDateTime createdAt,
        boolean edited,
        boolean isMine) {

    /**
     * @param name 탈퇴한 사용자면 실명 대신 고정 문구가 들어갑니다.
     *             탈퇴는 계정만 비활성이고 댓글은 남기므로(USER-03), 이름이 그대로 노출되면
     *             탈퇴의 의미가 없어집니다.
     */
    public record Author(Long id, String name) {

        private static final String WITHDRAWN_NAME = "탈퇴한 사용자";

        static Author of(CommentRow row) {
            return row.authorStatus() == UserStatus.WITHDRAWN
                    ? new Author(null, WITHDRAWN_NAME)
                    : new Author(row.authorId(), row.authorName());
        }
    }

    /**
     * 목록 쿼리가 삭제된 행을 이미 걸러 내므로 여기서는 삭제 분기를 다루지 않습니다.
     * 답글이 없어지면서 자리를 남길 이유도 사라졌습니다.
     */
    public static CommentResponse from(CommentRow row, Long viewerId) {
        return new CommentResponse(
                row.id(),
                row.content(),
                Author.of(row),
                row.createdAt(),
                row.isEdited(),
                row.authorId().equals(viewerId));
    }
}
