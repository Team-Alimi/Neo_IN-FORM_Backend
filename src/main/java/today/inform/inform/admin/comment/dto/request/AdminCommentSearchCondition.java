package today.inform.inform.admin.comment.dto.request;

/**
 * ADM-17 댓글 검색 조건.
 *
 * @param keyword        본문 부분 일치. 2글자 미만은 무시합니다 —
 *                       한 글자로는 전체와 다를 바 없고 목록만 무거워집니다
 * @param includeDeleted 삭제된 댓글도 볼지. 기본은 {@code false} 입니다.
 *                       삭제된 댓글은 본문이 지워져 있어({@code Comment#softDelete})
 *                       검색어로는 어차피 찾히지 않고, 목록에 자리만 차지합니다
 */
public record AdminCommentSearchCondition(
        String keyword,
        Long articleId,
        Long userId,
        boolean includeDeleted) {

    public boolean hasKeyword() {
        return keyword != null && keyword.trim().length() >= 2;
    }
}
