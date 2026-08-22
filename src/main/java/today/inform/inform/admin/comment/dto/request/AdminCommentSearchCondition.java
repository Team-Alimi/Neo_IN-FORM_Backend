package today.inform.inform.admin.comment.dto.request;

/**
 * ADM-17 댓글 검색 조건.
 *
 * <p><b>삭제된 댓글을 보는 옵션은 없습니다.</b> 답글이 없어지면서 자리를 남기는 삭제도 사라져,
 * 지워진 댓글은 행째 없습니다. 볼 수 있는 것이 영원히 아무것도 없는 토글은 두지 않습니다.
 *
 * @param keyword   본문 부분 일치. <b>2글자 미만은 무시</b>합니다 — pg_bigm 이 2-gram 이라
 *                  한 글자로는 인덱스가 후보를 좁히지 못해 전수 확인이 됩니다
 * @param articleId 특정 공지의 댓글만
 * @param userId    특정 작성자의 댓글만
 */
public record AdminCommentSearchCondition(
        String keyword,
        Long articleId,
        Long userId) {

    public boolean hasKeyword() {
        return keyword != null && keyword.trim().length() >= 2;
    }
}
