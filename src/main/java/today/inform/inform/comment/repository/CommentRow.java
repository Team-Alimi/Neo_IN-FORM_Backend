package today.inform.inform.comment.repository;

import java.time.OffsetDateTime;
import today.inform.inform.user.entity.UserStatus;

/**
 * 목록 조회용 한 줄. 댓글 본문 + 작성자 표시에 필요한 것만 담습니다.
 *
 * <p>엔티티로 받으면 작성자 이름을 찍는 순간 댓글 수만큼 users 조회가 나갑니다.
 * 한 번의 join 으로 끝내려고 별도 projection 을 둡니다.
 *
 * @param authorStatus 탈퇴 여부 판정에 씁니다. 탈퇴한 사용자의 이름은 화면에 내보내지 않습니다.
 */
public record CommentRow(
        Long id,
        Long parentId,
        String content,
        OffsetDateTime deletedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long authorId,
        String authorName,
        UserStatus authorStatus) {

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 수정된 적이 있는지.
     *
     * <p>{@code created_at} 과 {@code updated_at} 이 같으면 한 번도 안 고친 것입니다.
     * 두 컬럼 모두 {@code now()} 로 채워지는데 {@code now()} 는 트랜잭션 시작 시각이라,
     * 작성 트랜잭션 안에서는 정확히 같은 값이 됩니다.
     */
    public boolean isEdited() {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt);
    }
}
