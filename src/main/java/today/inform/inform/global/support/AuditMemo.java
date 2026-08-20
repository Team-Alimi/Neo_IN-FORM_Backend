package today.inform.inform.global.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.QueryFlushMode;
import org.springframework.stereotype.Component;

/**
 * 상태 변경 사유를 감사 로그에 남긴다.
 *
 * <p>행위자(`app.changed_by_user_id`)는 트랜잭션 매니저가 자동 주입하지만,
 * 사유(memo)는 요청마다 다르므로 서비스가 명시적으로 전달해야 한다.
 *
 * <p>DB trigger 가 `article_status_logs.memo` 를 채울 때 이 값을 읽으므로,
 * <b>상태를 바꾸는 UPDATE 보다 먼저</b> 호출해야 한다.
 *
 * <pre>
 * auditMemo.set("중복 병합 - 101,102 흡수");
 * articleRepository.updateStatus(ids, TRASHED);
 * </pre>
 *
 * <p>같은 트랜잭션 안에서만 유효하며, 커밋/롤백과 함께 사라진다.
 */
@Component
public class AuditMemo {

    private static final String SET_MEMO_SQL =
            "SELECT set_config('app.status_change_memo', ?1, true)";

    /** {@code article_status_logs.memo varchar(500)} */
    private static final int MAX_MEMO_LENGTH = 500;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @param memo 사유. {@code null} 이나 공백이면 아무것도 하지 않는다.
     *             DB 컬럼이 varchar(500) 이므로 초과분은 잘라 저장한다.
     */
    public void set(String memo) {
        if (memo == null || memo.isBlank()) {
            return;
        }
        String trimmed = memo.length() > MAX_MEMO_LENGTH ? memo.substring(0, MAX_MEMO_LENGTH) : memo;

        Query query = entityManager.createNativeQuery(SET_MEMO_SQL).setParameter(1, trimmed);

        // ★ auto-flush 를 꺼야 한다.
        //   Hibernate 는 native query 가 어떤 테이블을 건드리는지 알 수 없어서
        //   실행 전에 영속성 컨텍스트를 통째로 flush 한다.
        //   그러면 이미 더티한 엔티티의 UPDATE 가 이 set_config 보다 **먼저** 나가고,
        //   그 안에 상태 변경이 섞여 있으면 감사 로그의 memo 가 NULL 로 남는다.
        //   "상태를 바꾸는 UPDATE 보다 먼저 호출하라" 는 이 클래스의 계약이 조용히 깨지는 지점이다.
        query.unwrap(NativeQuery.class).setQueryFlushMode(QueryFlushMode.NO_FLUSH);

        query.getSingleResult();
    }
}
