package today.inform.inform.notification.repository;

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
     * CMT-05 답글 알림.
     *
     * <p><b>한 문장에 규칙 네 가지가 들어 있습니다.</b> 조회해서 자바로 판정하면
     * 그 사이 원댓글이 지워지거나 작성자가 탈퇴할 수 있고, 쿼리도 세 번 나갑니다.
     * <ul>
     *   <li>수신자는 원댓글 작성자 — 서브쿼리가 {@code comments} 에서 직접 가져옵니다</li>
     *   <li>{@code c.user_id <> :actorId} — <b>자기 댓글에 자기가 단 답글은 알리지 않습니다</b></li>
     *   <li>{@code u.status = 'ACTIVE'} — 탈퇴한 사용자에게는 만들지 않습니다.
     *       읽을 수 없는 행이라 테이블만 불립니다</li>
     *   <li>{@code ON CONFLICT DO NOTHING} — 유니크 인덱스
     *       {@code (user_id, article_id, type, dedup_key)} 위에서 중복 발송을 막습니다.
     *       재시도나 동시 요청이 겹쳐도 안전합니다</li>
     * </ul>
     *
     * @param replyId 답글 댓글 id. 중복 방지 키로 씁니다
     * @return 실제로 만들어졌으면 1. 대상이 없거나 이미 있으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO notifications (user_id, article_id, type, dedup_key, title, message)
            SELECT c.user_id, c.article_id, 'COMMENT_REPLY', CAST(:replyId AS varchar),
                   :title, :message
              FROM comments c
              JOIN users u ON u.id = c.user_id
             WHERE c.id = :parentId
               AND c.user_id <> :actorId
               AND c.deleted_at IS NULL
               AND u.status = 'ACTIVE'
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int createReplyNotification(@Param("parentId") Long parentId,
                                @Param("replyId") Long replyId,
                                @Param("actorId") Long actorId,
                                @Param("title") String title,
                                @Param("message") String message);
}
