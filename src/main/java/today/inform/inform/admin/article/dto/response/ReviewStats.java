package today.inform.inform.admin.article.dto.response;

/**
 * ADM-02 대시보드 카드 세 개.
 *
 * <p>각 숫자는 관리자가 눌러서 이동할 목록과 <b>같은 조건</b>으로 세야 합니다.
 * 카드에 12건이라고 떴는데 목록에 8건이 나오면 관리자는 무엇을 믿어야 할지 모릅니다.
 * 그래서 세 값 모두 목록 쿼리와 같은 저장소에서, 같은 조건 조립기를 써서 만듭니다.
 *
 * @param needsCheck 검수 대기 중 관리자가 <b>먼저</b> 봐야 할 것.
 *                   {@code pendingReview} 의 부분집합이라 합계가 전체와 맞지 않습니다
 */
public record ReviewStats(long pendingReview, long readyToPublish, long needsCheck) {
}
