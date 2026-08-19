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
 * <p>{@link Generated} 는 INSERT/UPDATE 직후 DB 값을 다시 읽어옵니다.
 * 이게 없으면 트리거가 바꾼 값이 영속성 컨텍스트에 반영되지 않아
 * 응답에 옛 시각이 실려 나갑니다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
