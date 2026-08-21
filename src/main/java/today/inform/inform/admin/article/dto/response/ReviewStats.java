package today.inform.inform.admin.article.dto.response;

/**
 * ADM-02 대시보드 (명세 4.8).
 *
 * <p>각 숫자는 관리자가 눌러서 이동할 목록과 <b>같은 조건</b>으로 세야 합니다.
 * 카드에 12건이라고 떴는데 목록에 8건이 나오면 관리자는 무엇을 믿어야 할지 모릅니다.
 * 그래서 전부 목록 쿼리와 같은 저장소에서, 같은 조건 조립기를 써서 만듭니다.
 *
 * <p><b>상태별 건수와 두 축은 합계가 맞지 않습니다.</b> 아래 둘은 상태의 부분집합이거나
 * 상태와 독립된 축이라 그렇습니다 — 화면에서 "합계 = 전체" 로 그리면 안 됩니다.
 *
 * @param duplicateSuspected 유사도가 임계값을 넘어 중복이 의심되는 것 (ADM-12)
 * @param needsReview        원본 수정이 감지되어 재검수 대기인 것.
 *                           명세는 {@code review_requested_at IS NOT NULL} 이라고 적고 있지만
 *                           그 컬럼은 스키마에서 제거됐습니다 — 재검수 정책이 "노출 유지 + 플래그" 에서
 *                           <b>"검수 대기로 강등"</b> 으로 바뀌었기 때문입니다.
 *                           그래서 판정은 {@code PENDING_REVIEW 이면서 published_at 이 있는} 것입니다
 */
public record ReviewStats(
        long draft,
        long pendingReview,
        long duplicateSuspected,
        long readyToPublish,
        long published,
        long trashed,
        long needsReview) {
}
