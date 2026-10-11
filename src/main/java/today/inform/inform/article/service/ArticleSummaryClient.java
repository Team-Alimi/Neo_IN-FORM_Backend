package today.inform.inform.article.service;

/** 요약 생성기의 외부 경계. 테스트에서는 실제 AI 대신 fake 를 주입합니다. */
public interface ArticleSummaryClient {

    boolean isEnabled();

    long reservationTokens(ArticleSummarySource source);

    Result generate(ArticleSummarySource source);

    /** content 는 {"lines":[...]} JSON. usage 가 없으면 totalTokens 는 0 입니다. */
    record Result(String content, long totalTokens) {
    }

    final class Failure extends RuntimeException {
        private final boolean retryable;

        public Failure(boolean retryable) {
            // HTTP 응답 본문, 원문, credential 을 예외에 담지 않습니다.
            super(retryable ? "Retryable summary failure" : "Non-retryable summary failure");
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
