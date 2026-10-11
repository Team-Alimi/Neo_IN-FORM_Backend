package today.inform.inform.article.service;

/**
 * ART-07 AI 요약 생성 진입점.
 *
 * <p><b>인터페이스로 둔 이유</b> — 호출 시점과 규칙은 이미 정해져 있는데
 * 생성 수단(LLM 공급자·프롬프트·비용 정책)만 미정입니다.
 * 호출부를 확정해 두면 나중에 구현체만 갈아 끼우면 됩니다.
 *
 * <p><b>반드시 비동기여야 합니다.</b> LLM 응답을 동기로 기다리면
 * 공지 첫 진입이 수 초 걸립니다. 상세 API 는 요약이 {@code null} 인 채로 즉시 응답하고,
 * 사용자는 다음 진입 때 요약을 봅니다.
 */
public interface ArticleSummaryGenerator {

    /**
     * 요약 생성을 <b>시작만</b> 합니다. 결과를 기다리지 않습니다.
     *
     * <p>worker 는 lock 획득 후 원문과 version/updated_at 을 함께 읽고 저장을 보호합니다.
     * 호출 트랜잭션이 있으면 커밋 후 작업을 등록하며 rollback 요청은 생성하지 않습니다.
     *
     * @param sourceUpdatedAt 호출자가 읽은 updated_at. 모르면 null 을 전달합니다.
     *                        값이 있으면 worker snapshot 과도 일치해야 합니다.
     */
    void requestSummary(Long articleId, java.time.OffsetDateTime sourceUpdatedAt);
}
