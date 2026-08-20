package today.inform.inform.article.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.repository.ArticleRepository;

/**
 * 조회수 집계.
 *
 * <p><b>왜 DB 를 바로 올리지 않는가</b> — 상세 진입마다 {@code UPDATE articles} 가 나가면
 * 인기 공지 한 건에 쓰기가 몰려 그 행의 잠금이 병목이 됩니다.
 * 관리자 수정과도 같은 행을 놓고 다투게 됩니다.
 * Redis 에 모았다가 주기적으로 한 번에 반영합니다.
 *
 * <p><b>중복 제거</b> — 같은 사용자가 30분 안에 다시 열면 세지 않습니다.
 * 새로고침으로 조회수를 부풀리는 걸 막습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleViewCounter {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

    private static final String SEEN_KEY_PREFIX = "inform:view:seen:";
    private static final String DELTA_KEY = "inform:view:delta";

    /**
     * 반영 작업 중인 델타를 옮겨 둘 키.
     *
     * <p>{@code HGETALL} 후 {@code DEL} 하면 그 사이 들어온 조회가 사라집니다.
     * {@code RENAME} 은 원자적이라 그 틈이 없습니다 — 옮긴 뒤 들어오는 조회는
     * 새로 만들어지는 {@link #DELTA_KEY} 에 쌓입니다.
     */
    private static final String FLUSHING_KEY = "inform:view:delta:flushing";

    private final StringRedisTemplate redis;
    private final ArticleRepository articleRepository;

    /**
     * 조회 1건 기록. 중복이면 아무것도 하지 않습니다.
     *
     * <p>Redis 가 죽어도 상세 조회는 성공해야 합니다. 조회수는 부가 정보이지
     * 공지를 못 보여줄 이유가 아닙니다.
     */
    public void recordView(Long articleId, Long userId) {
        try {
            String seenKey = SEEN_KEY_PREFIX + articleId + ":" + userId;
            Boolean firstView = redis.opsForValue().setIfAbsent(seenKey, "1", DEDUP_WINDOW);
            if (Boolean.TRUE.equals(firstView)) {
                redis.opsForHash().increment(DELTA_KEY, String.valueOf(articleId), 1L);
            }
        } catch (RuntimeException e) {
            log.warn("조회수 집계 실패. articleId={}", articleId, e);
        }
    }

    /**
     * 모아 둔 델타를 DB 에 더합니다.
     *
     * <p>★ 단일 인스턴스 전제입니다. 여러 대로 늘리면 두 인스턴스가 같은 델타를
     * 중복 반영할 수 있습니다. 스키마에 {@code shedlock} 테이블이 이미 있으므로
     * 그때 ShedLock 을 붙이면 됩니다.
     *
     * <p>{@code view_count} 는 재계산할 원천이 없는 값이라(V2 컬럼 주석)
     * 반영 도중 죽으면 그 구간은 복구되지 않습니다. 그래서 주기를 짧게 둡니다.
     */
    @Scheduled(fixedDelayString = "${inform.view-count.flush-interval-ms:60000}")
    @Transactional
    public void flush() {
        try {
            if (Boolean.FALSE.equals(redis.hasKey(FLUSHING_KEY))) {
                if (Boolean.FALSE.equals(redis.hasKey(DELTA_KEY))) {
                    return;
                }
                redis.rename(DELTA_KEY, FLUSHING_KEY);
            }
            // FLUSHING_KEY 가 이미 있었다면 지난 회차가 중간에 죽은 것이므로 이어서 처리합니다.

            Map<Object, Object> deltas = redis.opsForHash().entries(FLUSHING_KEY);

            // ★ article_id 오름차순으로 반영합니다. Redis 가 돌려주는 순서를 그대로 쓰면 안 됩니다.
            //   이 트랜잭션은 주기 동안 조회된 모든 공지 행의 배타 잠금을 커밋까지 쥡니다.
            //   같은 articles 행 여러 개를 잠그는 트랜잭션이 하나 더 있습니다 —
            //   북마크 일괄 삭제(BMK-04)가 카운터 트리거를 통해 그렇게 합니다.
            //   두 쪽의 획득 순서가 엇갈리면 서로를 기다리다 데드락이 납니다.
            //   순서를 한쪽이라도 고정해 두면 순환이 생길 여지가 줄어듭니다.
            List<Long> articleIds = deltas.keySet().stream()
                    .map(key -> Long.parseLong((String) key))
                    .sorted()
                    .toList();

            for (Long articleId : articleIds) {
                long delta = Long.parseLong((String) deltas.get(String.valueOf(articleId)));
                if (delta > 0) {
                    articleRepository.addViewCount(articleId, delta);
                }
            }
            redis.delete(FLUSHING_KEY);

            if (!deltas.isEmpty()) {
                log.debug("조회수 반영 완료. {}건", deltas.size());
            }
        } catch (RuntimeException e) {
            // 다음 회차가 FLUSHING_KEY 를 이어서 처리합니다.
            log.error("조회수 반영 실패", e);
        }
    }
}
