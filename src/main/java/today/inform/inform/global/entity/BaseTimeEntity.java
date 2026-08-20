package today.inform.inform.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * {@code created_at} / {@code updated_at} 을 가진 테이블의 공통 매핑.
 *
 * <p><b>두 값은 모두 DB 가 소유합니다.</b> 앱은 읽기만 합니다.
 * <ul>
 *   <li>{@code created_at} — 컬럼 DEFAULT now()</li>
 *   <li>{@code updated_at} — BEFORE UPDATE 트리거가 갱신</li>
 * </ul>
 *
 * <p>특히 {@code articles.updated_at} 은 "business 컬럼이 실제로 바뀐 경우에만" 갱신하는
 * 화이트리스트 트리거가 걸려 있습니다. 앱이 값을 실어 보내면 그 규칙이 무의미해지므로
 * {@code insertable=false, updatable=false} 로 막습니다.
 *
 * <p>{@link Generated} 는 INSERT 직후 DB 값을 다시 읽어옵니다.
 * 이게 없으면 DEFAULT 로 채워진 값이 영속성 컨텍스트에 반영되지 않아 null 이 됩니다.
 *
 * <p><b>★ UPDATE 시점에는 다시 읽지 않습니다. 일부러 그렇게 둡니다.</b>
 * {@code event = UPDATE} 를 넣으면 Hibernate 가 이 엔티티의 UPDATE 를
 * {@code INSERT ... RETURNING} 델리게이트 경로로 돌리는데,
 * 그 경로는 <b>갱신 행 수를 확인하지 않습니다.</b>
 * 그래서 {@code @Version} 이 붙은 엔티티에서 낙관적 잠금이 조용히 무력화됩니다 —
 * 충돌 시 {@code ObjectOptimisticLockingFailureException} 대신
 * {@code HibernateException("The database returned no natively generated values")} 가 올라오고,
 * 이건 SQLSTATE 가 없어 500 으로 나갑니다.
 * (통합 테스트 {@code ArticleOptimisticLockTest} 가 이 회귀를 막습니다)
 *
 * <p>대가로 <b>UPDATE 뒤의 {@code updatedAt} 은 메모리에서 낡습니다.</b>
 * 갱신된 값이 응답에 필요하면 flush 후 {@code EntityManager.refresh(entity)} 로 명시적으로 읽으세요.
 * 트리거가 바꾸는 다른 컬럼(요약·카운터)도 같은 SELECT 한 번에 함께 최신화됩니다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
