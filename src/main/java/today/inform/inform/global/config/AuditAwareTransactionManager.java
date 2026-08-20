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
            try {
                em.createNativeQuery(SET_ACTOR_SQL)
                        .setParameter(1, userId.toString())
                        .getSingleResult();
            } catch (RuntimeException e) {
                // ★ 여기서 예외를 그대로 던지면 안 된다.
                //   super.doBegin() 이 이미 EntityManagerHolder 를 스레드에 바인딩한 뒤라,
                //   doBegin 에서 예외가 나가면 Spring 은 doCleanupAfterCompletion 을 부르지 않는다
                //   (정리는 완료된 트랜잭션에 대해서만 돈다).
                //   결과적으로 홀더와 커넥션이 스레드에 남고, 그 스레드를 재사용하는 다음 요청이
                //   남의 EntityManager 를 물고 시작한다. 요청 하나 실패가 워커 하나 오염으로 번진다.
                //
                //   감사 행위자를 못 넣으면 changed_by 가 NULL 로 남고, 그건 스키마상
                //   "크롤러/시스템이 한 변경" 과 구분되지 않는다. 그래서 조용히 넘기지 않고 ERROR 로 남긴다.
                //   set_config 가 실패할 정도면 커넥션이 이미 깨진 상태라
                //   뒤따르는 업무 SQL 이 더 정확한 오류로 실패한다.
                log.error("감사 행위자 주입 실패. 이 트랜잭션의 감사 로그는 changed_by=NULL 로 남습니다. userId={}",
                        userId, e);
            }
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
