package today.inform.inform.article.dto.response;

/**
 * 요약만 떼어 보는 응답. <b>"생성 중" 안내를 띄운 화면이 재조회할 때 쓰는 전용 모양입니다.</b>
 *
 * <h2>★ 왜 상세 조회로 폴링하지 않는가</h2>
 * {@code GET /articles/{id}} 는 요약이 비어 있으면 <b>매번 생성 요청을 다시 던집니다.</b>
 * 그걸로 폴링하면 작업 큐(용량 20, 초과 시 거부)가 폴링 요청으로 채워져
 * <b>실제 생성 작업이 밀려 나갑니다.</b> 여기는 읽기만 합니다.
 *
 * <p>덧붙여 상세 응답은 분류·출처·첨부·북마크까지 조인한 큰 응답이고 조회수 집계로
 * 쓰기 트랜잭션을 엽니다. 한 필드를 확인하려고 그걸 반복할 이유가 없습니다.
 *
 * @param summary       요약 본문. {@code null} 일 수 있습니다
 * @param summaryStatus {@code summary} 가 왜 비었는지. {@link SummaryStatus} 참고
 */
public record ArticleSummaryStatusResponse(String summary, SummaryStatus summaryStatus) {

    public static ArticleSummaryStatusResponse of(String summary, SummaryStatus status) {
        return new ArticleSummaryStatusResponse(summary, status);
    }
}
