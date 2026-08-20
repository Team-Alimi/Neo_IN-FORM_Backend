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
     * @param sourceUpdatedAt 요약 대상 본문의 {@code updated_at}.
     *                        생성이 끝나 저장할 때 이 값으로 가드해야
     *                        그 사이 본문이 바뀐 경우 옛 요약이 붙지 않습니다.
     */
    void requestSummary(Long articleId, java.time.OffsetDateTime sourceUpdatedAt);
}
