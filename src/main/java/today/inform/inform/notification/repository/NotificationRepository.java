package today.inform.inform.notification.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * NTF-05 메일을 보내야 할 알림. <b>보낼 대상만 골라 옵니다 — 걸러내기는 SQL 에서 끝납니다.</b>
     *
     * <h2>★ 왜 기간을 자르는가</h2>
     * 메일 수신을 꺼 둔 사용자의 알림은 {@code email_sent_at} 이 <b>영원히 NULL</b> 입니다.
     * 조건에 기간이 없으면, 그 사용자가 나중에 설정에서 메일 수신을 켜는 순간
     * 쌓여 있던 지난 30일치(SYS-04 보관 기간)가 한꺼번에 날아갑니다.
     * V18 의 백필로 막은 것과 정확히 같은 종류의 소급 발송입니다.
     *
     * <p>창 밖으로 밀린 행은 보내지지도, 찍히지도 않은 채 남아 SYS-04 가 지울 때 사라집니다.
     * "안 보냈다" 를 "보냈다" 로 거짓 기록하지 않으려고 일부러 그대로 둡니다.
     *
     * <h2>★ 제외 조건</h2>
     * <ul>
     *   <li>{@code u.emailNotificationEnabled = TRUE} — 설정에서 끈 사용자(NTF-05 토글)</li>
     *   <li>{@code u.status = ACTIVE} — 탈퇴자. 주소가 남아 있어도 보내면 안 됩니다</li>
     * </ul>
     *
     * <h2>★ 왜 JPQL 인가</h2>
     * 생성·읽음 처리는 네이티브지만({@code ON CONFLICT} · 벌크 UPDATE 때문) 이쪽은
     * 특별한 SQL 이 필요하지 않습니다. JPQL 로 두면 <b>기동 시점에 검증</b>되어
     * 컬럼명을 틀렸을 때 운영이 아니라 실행 직후에 드러납니다.
     *
     * <p>{@code ORDER BY n.id} — 같은 배치에서 만들어진 알림은 {@code created_at} 이
     * 전부 같습니다({@code now()} 가 트랜잭션 시작 시각으로 고정). 정렬이 불안정하면
     * 한도에 걸려 잘린 뒤 다음 주기에 어떤 행이 올지 알 수 없습니다.
     *
     * @param createdAfter 이 시각 <b>이후</b>에 만들어진 알림만 대상입니다
     * @param pageable     한 번에 가져올 최대 건수.
     *                     SES 일일 한도를 한 번에 태우지 않으려는 상한입니다
     */
    @Query("""
            SELECT new today.inform.inform.notification.repository.PendingEmail(
                       n.id, u.id, u.email, n.title, n.message)
              FROM Notification n
              JOIN User u ON u.id = n.userId
             WHERE n.emailSentAt IS NULL
               AND n.createdAt > :createdAfter
               AND u.emailNotificationEnabled = TRUE
               AND u.status = today.inform.inform.user.entity.UserStatus.ACTIVE
             ORDER BY n.id
            """)
    List<PendingEmail> findPendingEmails(
            @Param("createdAfter") OffsetDateTime createdAfter, Pageable pageable);

    /**
     * NTF-05 발송 완료 기록. <b>메일 한 통을 보낸 직후 그 한 건만 즉시 커밋합니다.</b>
     *
     * <h2>★ 왜 메서드에 {@code @Transactional} 이 붙어 있는가</h2>
     * 이 저장소의 다른 쓰기 메서드는 트랜잭션을 호출한 서비스에서 받습니다.
     * 이것만 스스로 엽니다 — 발송 루프를 트랜잭션 안에 둘 수 없기 때문입니다.
     * 루프 전체를 한 트랜잭션으로 묶으면 두 가지가 깨집니다.
     * <ul>
     *   <li><b>끝에서 롤백되면 메일은 이미 나갔는데 기록이 사라집니다.</b>
     *       다음 주기가 같은 알림을 또 보냅니다. 메일은 되돌릴 수 없습니다</li>
     *   <li>SES 호출 사이의 대기(1통/초 제한) 동안 DB 커넥션을 수십 초 잡습니다</li>
     * </ul>
     *
     * <h2>★ 0 이 돌아오는 경우</h2>
     * 조건에 {@code email_sent_at IS NULL} 이 들어가 있어, 그 사이 다른 실행이 같은 행을
     * 이미 찍었으면 0 입니다. 그건 <b>같은 메일이 두 번 나갔다는 뜻</b>이므로 호출부가
     * 로그로 남깁니다 — 배치 락이 제 역할을 못 하고 있다는 신호입니다.
     *
     * @return 1 이면 기록됨. 0 이면 이미 다른 실행이 보낸 뒤입니다
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notifications SET email_sent_at = now() "
            + "WHERE id = :id AND email_sent_at IS NULL", nativeQuery = true)
    int markEmailSent(@Param("id") Long id);

}
