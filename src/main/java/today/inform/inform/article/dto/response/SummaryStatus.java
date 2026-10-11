package today.inform.inform.article.dto.response;

/**
 * AI 요약의 현재 처지. <b>{@code summary} 가 {@code null} 인 이유를 구분하기 위한 값입니다.</b>
 *
 * <p>이 값이 없으면 프론트는 "요약이 없는 공지" 와 "지금 만드는 중" 과 "생성이 꺼짐" 을
 * 전부 같은 {@code summary: null} 로 받습니다. 그래서 "생성 중" 안내도 못 띄우고,
 * 재조회를 해야 하는지도 알 수 없습니다.
 *
 * <p><b>DB 컬럼이 아닙니다.</b> 조회 시각에 계산합니다 — {@code DeadlineStatus} 와 같습니다.
 */
public enum SummaryStatus {

    /** 요약이 있습니다. {@code summary} 가 채워져 있습니다. */
    READY,

    /**
     * 생성을 요청했습니다. 잠시 뒤 {@code GET /articles/{id}/summary} 로 다시 보면 채워집니다.
     *
     * <p><b>"요청을 걸었다" 까지만 보장합니다.</b> 실제로 생성이 실패하거나 쿨다운에 걸려
     * 돌지 않는 경우도 여기에 들어옵니다 — 그 판정은 워커 안에서 일어나서 응답 시점에는
     * 알 수 없습니다. 그래서 <b>무한 폴링은 안 됩니다.</b> 몇 번 보고 안 되면 포기하세요.
     */
    PENDING,

    /**
     * 생성하지 않습니다. 기다려도 안 생깁니다 — <b>재조회하지 마세요.</b>
     *
     * <p>요약 기능이 꺼져 있거나(API 키 미주입) 작업 등록 자체가 거부된 경우입니다.
     */
    NONE
}
