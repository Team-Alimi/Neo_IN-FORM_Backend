package today.inform.inform.admin.article.service;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.exception.SqlStateErrorMapper;

/**
 * 벌크 작업을 <b>건별 트랜잭션</b>으로 돌립니다.
 *
 * <h2>왜 별도 빈인가</h2>
 * {@code @Transactional} 은 프록시가 가로채므로 <b>같은 객체 안에서 부르면 걸리지 않습니다.</b>
 * 서비스가 자기 메서드를 반복 호출하는 형태로 짜면 트랜잭션이 하나로 합쳐지고,
 * 그러면 한 건이 실패할 때 이미 성공한 건까지 되돌아가 <b>부분 성공이 조용히 무너집니다.</b>
 * 오류가 아니라 "왜 다 실패했지" 로만 보이므로 알아채기 어렵습니다.
 *
 * <h2>트랜잭션은 왜 별도 빈인가</h2>
 * {@code @Transactional} 은 프록시가 가로채므로 <b>같은 클래스 안에서 부르면 걸리지 않습니다.</b>
 * 그러면 트랜잭션 없이 실행되어 엔티티 변경이 UPDATE 로 나가지 않는데,
 * 예외도 없고 결과도 "성공" 이라 <b>오류 없이 아무 일도 안 일어납니다.</b>
 * 그래서 {@link BulkUnitRunner} 로 분리했습니다.
 *
 * <h2>왜 REQUIRES_NEW 인가</h2>
 * {@code REQUIRED} 로 두면 <b>바깥에 트랜잭션이 생기는 순간 조용히 무너집니다.</b>
 * 건별 트랜잭션이 그 바깥으로 합쳐져, 한 건이 실패하면 전체가 rollback-only 로 표시되고
 * 이미 성공한 건까지 되돌아갑니다. 오류가 아니라 "왜 다 실패했지" 로만 보입니다.
 * 지금은 호출부에 트랜잭션이 없지만, 그건 <b>나중에 누가 {@code @Transactional} 한 줄을 붙이면
 * 깨지는 전제</b>입니다. 그런 전제 위에 부분 성공을 세우지 않습니다.
 *
 * <p>대가로 바깥 트랜잭션이 있으면 커넥션을 두 개 점유합니다.
 * 관리자 벌크 작업은 동시 실행이 많지 않아 감당할 수 있는 비용입니다.
 *
 * <p><b>★ 그래서 이 경로는 {@code @Transactional} 테스트로 검증할 수 없습니다.</b>
 * 테스트 트랜잭션은 커밋되지 않으므로 새 트랜잭션이 <b>준비한 데이터를 보지 못합니다.</b>
 * 부분 성공은 부분 커밋이라, 실제로 커밋하는 테스트로만 확인됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkExecutor {

    /**
     * 건별 트랜잭션은 <b>다른 빈</b>이 엽니다.
     *
     * <p>여기서 {@code @Transactional} 메서드를 만들어 자기 자신을 부르면
     * 프록시를 거치지 않아 <b>트랜잭션이 아예 생기지 않습니다</b> —
     * 그러면 엔티티 변경이 UPDATE 로 나가지 않는데 예외도 안 납니다.
     * ({@code BulkUnitRunner} 주석 참조)
     */
    private final BulkUnitRunner unitRunner;

    /**
     * 각 id 에 대해 {@code unit} 을 한 트랜잭션씩 실행하고 결과를 모읍니다.
     *
     * <p>중복과 {@code null} 을 먼저 걸러 냅니다 — 같은 항목이 두 번 담긴 요청에서
     * 두 번째가 "이미 처리됨" 으로 실패해 보이지 않도록.
     */
    public BulkResult runEach(List<Long> ids, Consumer<Long> unit) {
        BulkResult.Builder result = BulkResult.builder();

        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            try {
                unitRunner.run(id, unit);
                result.succeed(id);
            } catch (BusinessException e) {
                result.fail(id, e.getErrorCode(), e.getMessage());
            } catch (RuntimeException e) {
                // DB 제약·트리거 위반은 커밋 시점에 터집니다. SQLSTATE 를 우리 코드로 옮겨
                // 화면이 다른 실패와 같은 방식으로 다룰 수 있게 합니다.
                ErrorCode mapped = SqlStateErrorMapper.resolve(e);
                if (mapped == null) {
                    // 정말 모르는 실패. 한 건 때문에 배치 전체를 세우지는 않되 로그로 남깁니다.
                    log.error("벌크 작업 중 예상 못한 실패. id={}", id, e);
                    mapped = ErrorCode.INTERNAL_SERVER_ERROR;
                }
                String message = SqlStateErrorMapper.extractDbMessage(e);
                result.fail(id, mapped, message != null ? message : mapped.getMessage());
            }
        }
        return result.build();
    }

}
