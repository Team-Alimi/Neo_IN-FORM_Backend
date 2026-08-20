package today.inform.inform.article.service;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 아직 요약 생성 수단이 없어서 요청만 기록합니다.
 *
 * <p>조용히 아무것도 안 하는 대신 로그를 남깁니다 —
 * "요약이 계속 null 인데 왜지" 를 추적할 수 있어야 합니다.
 *
 * <p>실제 구현이 생기면 <b>이 클래스를 지우세요.</b>
 * {@code @ConditionalOnMissingBean} 으로 자동으로 물러나게 하려 했으나 동작하지 않습니다 —
 * 그 조건은 자동설정 클래스에서만 평가되고, 컴포넌트 스캔 대상에 붙이면
 * 다른 빈이 등록되기 전에 판정되어 의미가 없습니다.
 */
@Slf4j
@Component
public class NoOpArticleSummaryGenerator implements ArticleSummaryGenerator {

    @Override
    public void requestSummary(Long articleId, OffsetDateTime sourceUpdatedAt) {
        log.info("요약 생성 요청을 받았으나 생성기가 없습니다. articleId={}", articleId);
    }
}
