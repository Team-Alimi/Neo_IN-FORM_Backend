package today.inform.inform.global.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import today.inform.inform.global.security.CurrentUserProvider;

/**
 * 쓰기 트랜잭션을 시작할 때 감사 행위자를 DB 세션에 주입한다.
 *
 * <p>DB trigger 가 `articles.status` / `users.role` 변경을 감사 로그에 자동 기록하는데,
 * "누가 했는지"는 트리거가 알 수 없으므로 트랜잭션-로컬 GUC 로 전달해야 한다.
 *
 * <p><b>왜 트랜잭션 매니저에서 하는가</b>
 * 서비스마다 손으로 호출하게 두면 반드시 누군가 빠뜨린다. 그런데 빠뜨려도
 * 예외가 나지 않고 `changed_by = NULL` 이 되는데, 그건 "크롤러/시스템이 한 변경"의
 * 정상값이라 사후에 사고와 정상을 구분할 수 없다. 그래서 진입점에서 강제한다.
 *
 * <p><b>왜 set_config() 인가</b>
 * {@code SET LOCAL app.changed_by_user_id = ?} 는 bind parameter 를 받지 못한다.
 * 문자열을 직접 이어 붙이면 인젝션 경로가 생기므로 bind 를 지원하는 함수를 쓴다.
 * 세 번째 인자 {@code true} 가 transaction-local 이며, {@code false} 로 두면
 * 커넥션 풀 반납 후에도 값이 남아 <b>다음 요청이 다른 사용자 이름으로 감사 로그를 남긴다.</b>
 *
 * <p>읽기 전용 트랜잭션은 감사 대상이 아니므로 건너뛴다(불필요한 커넥션 선점 방지).
 */
@Slf4j
public class AuditAwareTransactionManager extends JpaTransactionManager {

    private static final String SET_ACTOR_SQL =
            "SELECT set_config('app.changed_by_user_id', ?1, true)";

    private final transient CurrentUserProvider currentUserProvider;

    public AuditAwareTransactionManager(EntityManagerFactory emf, CurrentUserProvider currentUserProvider) {
        super(emf);
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        if (definition.isReadOnly()) {
            return;
        }

        currentUserProvider.currentUserId().ifPresent(userId -> {
            EntityManager em = currentEntityManager();
            if (em == null) {
                // 트랜잭션이 시작됐는데 EntityManager 가 없는 상황은 정상 경로에 없다.
                // 조용히 넘기면 감사 누락이 되므로 로그로 남긴다.
                log.warn("감사 행위자를 주입하지 못했습니다 (EntityManager 없음). userId={}", userId);
                return;
            }
            em.createNativeQuery(SET_ACTOR_SQL)
                    .setParameter(1, userId.toString())
                    .getSingleResult();
        });
    }

    private EntityManager currentEntityManager() {
        EntityManagerFactory emf = getEntityManagerFactory();
        if (emf == null) {
            return null;
        }
        Object resource = TransactionSynchronizationManager.getResource(emf);
        return (resource instanceof EntityManagerHolder holder) ? holder.getEntityManager() : null;
    }
}
