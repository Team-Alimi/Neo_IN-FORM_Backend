package today.inform.inform.admin.article.service;

import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 벌크 작업의 한 건을 <b>독립된 트랜잭션</b>에서 실행합니다.
 *
 * <h2>왜 {@code BulkExecutor} 안에 두지 않는가</h2>
 * {@code @Transactional} 은 <b>프록시가 가로챕니다.</b> 같은 클래스 안에서 부르면
 * 프록시를 거치지 않으므로 애너테이션이 <b>아무 일도 하지 않습니다.</b>
 *
 * <p>그 결과가 특히 나쁩니다. 트랜잭션이 없으면 조회한 엔티티가 영속 상태가 아니라
 * 필드를 바꿔도 <b>UPDATE 가 나가지 않습니다.</b> 예외도 없고 반환값도 정상이라
 * 서비스는 "성공" 을 돌려주고 DB 는 그대로입니다 — 오류 없이 아무 일도 안 일어납니다.
 * 실제로 이 코드가 그렇게 한 번 깨졌고, 통합 테스트가 "성공했다는데 상태가 그대로" 로 잡았습니다.
 *
 * <p>그래서 <b>별도 빈</b>으로 뺍니다. 클래스가 다르면 호출이 반드시 프록시를 지납니다.
 */
@Component
public class BulkUnitRunner {

    /**
     * {@code REQUIRES_NEW} 입니다.
     *
     * <p>{@code REQUIRED} 로 두면 바깥에 트랜잭션이 생기는 순간 건별 트랜잭션이 그리로 합쳐지고,
     * 한 건이 실패할 때 전체가 rollback-only 로 표시되어 <b>부분 성공이 조용히 무너집니다.</b>
     * 지금은 호출부에 트랜잭션이 없지만, 그건 나중에 누가 {@code @Transactional} 한 줄을 붙이면
     * 깨지는 전제입니다. 그런 전제 위에 부분 성공을 세우지 않습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Long id, Consumer<Long> unit) {
        unit.accept(id);
    }
}
