package today.inform.inform.notification.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * NTF-01 목록. 최신순.
     *
     * <p>정렬을 {@code Pageable} 이 아니라 쿼리에 박습니다.
     * 클라이언트가 보낸 정렬을 그대로 받으면 Spring Data 가 검증 없이 이어 붙여
     * 없는 속성 하나에 500 이 납니다(댓글 목록에서 겪은 것과 같은 문제).
     * 알림은 최신순 외에 정렬할 이유가 없습니다.
     *
     * <p>{@code id DESC} 를 붙이는 이유는 마감 알림 배치가 한 트랜잭션에서 수백 건을
     * 만들기 때문입니다. {@code now()} 가 트랜잭션 시작 시각으로 고정이라
     * <b>같은 배치에서 생성된 알림은 created_at 이 전부 같습니다.</b>
     * tie-breaker 가 없으면 페이지 경계에서 중복·누락이 생깁니다.
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId ORDER BY n.createdAt DESC, n.id DESC")
    Page<Notification> findPage(@Param("userId") Long userId, Pageable pageable);

    /** NTF-02 배지. 부분 인덱스({@code idx_notifications_unread})를 탑니다. */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * NTF-03 개별 읽음.
     *
     * <p>조회 후 수정이 아니라 한 문장입니다. 소유자 확인이 {@code WHERE} 절에 들어가 있어
     * <b>남의 알림을 읽음 처리할 수 없습니다.</b> 따로 확인하지 않아도 됩니다.
     *
     * <p>{@code COALESCE} 를 쓰는 이유 — 이미 읽은 알림을 다시 호출해도 1행이 갱신되어
     * 멱등하게 200 이 나가면서, <b>최초 읽은 시각은 그대로 보존</b>됩니다.
     * 조건에 {@code read_at IS NULL} 을 넣었다면 재호출이 0행이 되어
     * "내 알림이 아님" 과 구분할 수 없었습니다.
     *
     * @return 1 이면 처리됨. 0 이면 없거나 남의 알림입니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notifications SET read_at = COALESCE(read_at, now()) "
            + "WHERE id = :id AND user_id = :userId", nativeQuery = true)
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * NTF-04 전체 읽음.
     *
     * @return 이번에 읽음 처리된 개수. 이미 다 읽었으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notifications SET read_at = now() "
            + "WHERE user_id = :userId AND read_at IS NULL", nativeQuery = true)
    int markAllRead(@Param("userId") Long userId);

    /**
     * SYS-03 마감 D-1 알림 생성. <b>대상 전체를 한 문장으로 넣습니다.</b>
     *
     * <h2>★ 왜 네이티브인가</h2>
     * {@code uk_notifications_dedup} 위에서 {@code ON CONFLICT DO NOTHING} 을 써야 하는데
     * JPA 로는 표현할 수 없습니다. 이 한 조각 때문에 알림 생성이 미구현으로 남아 있었습니다.
     *
     * <h2>★ 왜 루프가 아닌가</h2>
     * 공지를 읽어 와 사용자마다 {@code save()} 를 부르면 쿼리가
     * <b>공지 수 × 북마크 수</b>만큼 나갑니다. 인기 공지 하나에 북마크가 수백 개면
     * 09시에 그만큼의 INSERT 가 쏟아집니다. {@code INSERT ... SELECT} 는 한 번입니다.
     *
     * <h2>★ 멱등합니다 — 재실행도, 다중 인스턴스도 안전합니다</h2>
     * 중복은 {@code ON CONFLICT} 가 흡수합니다. 그래서 배치가 두 번 돌아도 1건이고,
     * 인스턴스를 여러 대로 늘려도 ShedLock 이 필요하지 않습니다
     * (조회수 반영은 <b>증분</b>이라 중복이 누적되지만, 이쪽은 유니크 키가 막습니다).
     *
     * <h2>★ 마감일이 연장되면 다시 보냅니다</h2>
     * {@code dedup_key} 가 마감일이라, 마감이 미뤄지면 키가 바뀌어 새 알림이 들어갑니다.
     * 의도된 동작입니다 — 날짜가 바뀐 것은 사용자가 다시 알아야 하는 정보입니다.
     *
     * <p>제외 조건 두 개가 중요합니다.
     * <ul>
     *   <li>{@code u.status = 'ACTIVE'} — 탈퇴자에게 알림을 쌓지 않습니다(명세 3.2 각주)</li>
     *   <li>{@code a.status = 'PUBLISHED'} — 내려간 공지의 마감을 알리지 않습니다</li>
     * </ul>
     *
     * <p>{@code to_char} 의 {@code FM} 은 0 채움을 없애고({@code 01월} → {@code 1월}),
     * 큰따옴표는 한글을 형식 문자로 해석하지 않게 묶습니다.
     *
     * @param deadline 알림을 보낼 마감일. <b>앱 시계로 계산해 넘기세요</b> —
     *                 SQL 의 {@code CURRENT_DATE} 를 쓰면 DB(UTC)와 앱(KST)의 "내일" 이
     *                 KST 00~09시에 하루 어긋납니다. 이 배치는 09시에 돌아 특히 위험합니다
     * @return 실제로 들어간 건수. 중복으로 건너뛴 것은 세지 않습니다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO notifications (user_id, article_id, type, dedup_key, title, message)
            SELECT b.user_id,
                   a.id,
                   'DEADLINE_D1',
                   to_char(a.ends_on, 'YYYY-MM-DD'),
                   '마감이 하루 남았습니다',
                   a.title || ' — ' || to_char(a.ends_on, 'FMMM"월" FMDD"일"') || ' 마감'
              FROM articles a
              JOIN bookmarks b ON b.article_id = a.id
              JOIN users     u ON u.id = b.user_id
             WHERE a.ends_on = :deadline
               AND a.status = 'PUBLISHED'
               AND u.status = 'ACTIVE'
            ON CONFLICT (user_id, article_id, type, dedup_key) WHERE article_id IS NOT NULL
            DO NOTHING
            """, nativeQuery = true)
    int createDeadlineNotifications(@Param("deadline") LocalDate deadline);

    /**
     * SYS-04 오래된 알림 정리. 매일 한 번 돌면 하루치씩 지워집니다.
     *
     * <p><b>읽음 여부를 보지 않습니다.</b> 30일이 지난 알림은 안 읽었더라도 지웁니다 —
     * 마감이 한 달 전에 끝난 공지를 알려 주는 배지는 정보가 아니라 노이즈입니다.
     *
     * <p>{@code LIMIT} 을 두지 않았습니다. 매일 도는 동안에는 하루치라 소량이고,
     * 한동안 멈췄다가 다시 돌면 그만큼 커지지만 그때도 한 번의 DELETE 로 끝납니다.
     * 수십만 건 규모가 되면 그때 배치 삭제를 검토하세요.
     *
     * @param cutoff 이 시각보다 먼저 만들어진 알림을 지웁니다
     * @return 지운 건수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM notifications WHERE created_at < :cutoff", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);

}
