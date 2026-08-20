package today.inform.inform.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 인앱 알림.
 *
 * <p><b>생성은 이 엔티티로 하지 않습니다.</b> 중복 발송을 막는 유니크 인덱스
 * {@code (user_id, article_id, type, dedup_key)} 위에서
 * {@code INSERT ... ON CONFLICT DO NOTHING} 을 써야 하는데, JPA 로는 표현할 수 없습니다.
 * 저장소의 native INSERT 를 씁니다.
 *
 * <p>읽음 처리도 벌크 UPDATE 입니다 — 목록에서 "전체 읽음" 을 누르면
 * 수백 건을 한 문장으로 처리해야 합니다.
 *
 * <p>결국 이 엔티티는 <b>목록 조회 전용</b>입니다.
 * 그래도 두는 이유는 조회 결과를 타입 있는 형태로 다루기 위해서입니다.
 *
 * <p>{@code created_at} 만 있고 {@code updated_at} 이 없어 {@code BaseTimeEntity} 를
 * 상속하지 않습니다. 알림은 수정되는 자원이 아니라 읽음 표시만 붙습니다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 연결된 공지. 공지와 무관한 알림도 있을 수 있어 nullable 입니다. */
    @Column(name = "article_id", updatable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 255, updatable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text", updatable = false)
    private String message;

    /** 읽은 시각. NULL 이면 안 읽음. 배지 개수가 이 값을 셉니다. */
    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isRead() {
        return readAt != null;
    }
}
