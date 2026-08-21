package today.inform.inform.admin.article.dto.response;

import java.util.ArrayList;
import java.util.List;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 벌크 작업 공통 응답 (명세 4.8 "벌크 작업 — 공통 규약").
 *
 * <p><b>부분 성공을 허용합니다.</b> 관리자가 30건을 골랐는데 하나가 전이 규칙에 걸렸다고
 * 나머지 29건까지 되돌리면, 관리자는 <b>어느 것이 문제였는지 목록에서 직접 찾아야</b> 합니다.
 * 실패한 건만 사유와 함께 돌려주면 그 자리에서 알 수 있습니다.
 *
 * <p>그래서 건별로 <b>트랜잭션을 나눕니다</b>({@code BulkExecutor}).
 * 한 트랜잭션에 묶으면 한 건이 실패하는 순간 그 트랜잭션 전체가 rollback-only 로 표시되어
 * 이미 처리한 것까지 되돌아갑니다 — 부분 성공이 성립하지 않습니다.
 */
public record BulkResult(List<Long> succeeded, List<Failure> failed) {

    /**
     * @param code {@code ErrorCode} 의 코드 문자열. 화면이 분기할 수 있도록 사람이 읽는 문구와 따로 줍니다
     */
    public record Failure(Long id, String code, String message) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Long> succeeded = new ArrayList<>();
        private final List<Failure> failed = new ArrayList<>();

        public void succeed(Long id) {
            succeeded.add(id);
        }

        public void fail(Long id, ErrorCode code, String message) {
            failed.add(new Failure(id, code.getCode(), message));
        }

        public BulkResult build() {
            return new BulkResult(List.copyOf(succeeded), List.copyOf(failed));
        }
    }
}
