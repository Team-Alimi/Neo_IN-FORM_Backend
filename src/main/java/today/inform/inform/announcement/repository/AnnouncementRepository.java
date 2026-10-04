package today.inform.inform.announcement.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;

/**
 * 서비스 공지 조회.
 *
 * <h2>★ 노출 판정이 두 곳에 있습니다</h2>
 * 같은 규칙을 {@link Announcement#isVisibleOn(LocalDate)} 이 자바로, 이 파일의 JPQL 이 SQL 로
 * 각각 판정합니다. <b>한쪽만 고치면 "목록에는 있는데 상세는 404" 같은 어긋남이 생깁니다.</b>
 * 둘 다 고치세요. (공지 목록의 {@code DeadlineStatus} 와 같은 구조입니다)
 *
 * <pre>
 *   status = PUBLISHED
 *   AND (starts_on IS NULL OR starts_on &lt;= 오늘)
 *   AND (ends_on   IS NULL OR ends_on   &gt;= 오늘)
 * </pre>
 *
 * <h2>★ {@code today} 를 앱에서 넘깁니다</h2>
 * SQL 의 {@code CURRENT_DATE} 를 쓰면 DB(UTC)와 앱(KST, {@code -Duser.timezone=Asia/Seoul})의
 * "오늘" 이 KST 00~09시에 하루 어긋납니다. 자바 쪽 판정과 시계를 맞추려면 앱이 넘겨야 합니다.
 * ({@code ArticleQueryRepository.appendDeadlineStatus} 와 같은 이유)
 *
 * <h2>★ 기간은 목록·상세·팝업에 모두 걸립니다</h2>
 * 기간을 팝업에만 적용하면 <b>{@code starts_on} 이 미래인 예약 공지가 목록에는 보입니다.</b>
 * {@code id} 가 bigserial 이라 상세 주소도 쉽게 맞출 수 있어, 아직 알리지 않은 안내가 먼저 샙니다.
 * 그래서 세 경로가 같은 조건을 씁니다 — 기간이 지난 공지는 상세도 404 입니다.
 * 지난 안내를 계속 보여 줘야 한다면 {@code ends_on} 을 비워 두는 것이 맞습니다.
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * 사용자 목록. 최신 발행순 고정입니다.
     *
     * <p><b>{@code pageable} 의 정렬은 쓰지 않습니다.</b> 호출부가 정렬 없는
     * {@code PageRequest} 를 넘겨야 합니다 — Spring Data 는 클라이언트가 보낸 {@code sort} 를
     * 검증 없이 ORDER BY 뒤에 이어 붙이므로, {@code ?sort=foo} 하나면 JPQL 파싱에서 터지고
     * SQLSTATE 가 없어 500 이 나갑니다. ({@code VendorRepository.search} 와 같은 이유)
     *
     * <p>{@code id DESC} 를 tie-breaker 로 붙입니다. 같은 날 여러 건을 발행하면
     * {@code published_at} 이 거의 같아지고, 정렬 키가 유일하지 않으면 페이지 경계에서
     * 같은 공지가 두 번 나오거나 누락됩니다. {@code idx_ann_feed} 도 같은 순서입니다.
     */
    @Query("""
            SELECT a FROM Announcement a
             WHERE a.status = today.inform.inform.announcement.entity.AnnouncementStatus.PUBLISHED
               AND (a.startsOn IS NULL OR a.startsOn <= :today)
               AND (a.endsOn   IS NULL OR a.endsOn   >= :today)
             ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Page<Announcement> findVisible(@Param("today") LocalDate today, Pageable pageable);

    /**
     * 사용자 상세. 노출 조건을 통과한 것만 찾습니다.
     *
     * <p>{@code findById} 를 쓰면 임시저장·보관 공지가 번호만 알면 열립니다.
     * 비어 있으면 호출부가 {@code ANNOUNCEMENT_NOT_FOUND} 로 돌려줍니다 —
     * 403 이 아니라 404 입니다. "있지만 못 본다" 를 알려 주면 그 자체가 정보입니다.
     */
    @Query("""
            SELECT a FROM Announcement a
             WHERE a.id = :id
               AND a.status = today.inform.inform.announcement.entity.AnnouncementStatus.PUBLISHED
               AND (a.startsOn IS NULL OR a.startsOn <= :today)
               AND (a.endsOn   IS NULL OR a.endsOn   >= :today)
            """)
    Optional<Announcement> findVisibleById(@Param("id") Long id, @Param("today") LocalDate today);

    /**
     * 지금 띄울 팝업 전체.
     *
     * <p><b>페이징하지 않고, 건수를 자르지도 않습니다.</b> 팝업은 여러 건이 동시에 켜질 수 있다는
     * 것이 정책이고, 여기서 상한을 두면 <b>관리자가 켠 팝업이 조용히 안 뜨는</b> 경우가 생깁니다.
     * 대신 관리자 쪽에서 "이미 N건이 켜져 있다" 고 경고합니다
     * ({@code AdminAnnouncementService}).
     *
     * <p>목록과 같은 정렬입니다. 클라이언트는 앞에서부터 쌓아 올리면 됩니다.
     */
    @Query("""
            SELECT a FROM Announcement a
             WHERE a.status = today.inform.inform.announcement.entity.AnnouncementStatus.PUBLISHED
               AND a.popup = true
               AND (a.startsOn IS NULL OR a.startsOn <= :today)
               AND (a.endsOn   IS NULL OR a.endsOn   >= :today)
             ORDER BY a.publishedAt DESC, a.id DESC
            """)
    List<Announcement> findPopups(@Param("today") LocalDate today);

    /**
     * 관리 화면 목록. <b>임시저장·보관까지 전부</b> 보입니다.
     *
     * <p>{@code id DESC} 로 정렬합니다 — 관리자는 "방금 쓴 것" 부터 봐야 하고,
     * {@code published_at} 은 임시저장 공지에 없어서 정렬 기준이 될 수 없습니다.
     * {@code id} 가 bigserial 이라 작성 순서와 같습니다.
     *
     * <p>세 조건 모두 {@code null} 이면 필터 없음입니다.
     */
    @Query("""
            SELECT a FROM Announcement a
             WHERE (:status IS NULL OR a.status = :status)
               AND (:type   IS NULL OR a.type   = :type)
               AND (:popup  IS NULL OR a.popup  = :popup)
             ORDER BY a.id DESC
            """)
    Page<Announcement> search(@Param("status") AnnouncementStatus status,
                              @Param("type") AnnouncementType type,
                              @Param("popup") Boolean popup,
                              Pageable pageable);
}
