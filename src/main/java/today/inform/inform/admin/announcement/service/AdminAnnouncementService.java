package today.inform.inform.admin.announcement.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.announcement.dto.request.CreateAnnouncementRequest;
import today.inform.inform.admin.announcement.dto.request.UpdateAnnouncementRequest;
import today.inform.inform.admin.announcement.dto.response.AdminAnnouncementResponse;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;
import today.inform.inform.announcement.repository.AnnouncementRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 서비스 공지 관리. {@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다({@code SecurityConfig}).
 *
 * <p><b>DELETE 는 없습니다.</b> 공지는 "무엇을 언제 알렸는지" 의 기록이기도 해서 지우면
 * 나중에 확인할 방법이 없습니다. 내리는 것은 보관(ARCHIVED)으로 갈음합니다.
 *
 * <h2>★ 이 서비스의 핵심은 경고입니다</h2>
 * 공지는 <b>조작이 조용히 아무 일도 하지 않는</b> 경우가 유독 많습니다.
 * <ul>
 *   <li>팝업을 켰지만 임시저장 — 저장은 성공하고 화면에도 "팝업 ON" 으로 보이는데 안 뜹니다</li>
 *   <li>종료일이 지난 공지를 발행 — 발행은 성공하고 상태도 PUBLISHED 인데 목록에 없습니다</li>
 *   <li>시작일이 미래 — 같은 모양이지만 이쪽은 의도된 예약입니다. 구분해 줘야 합니다</li>
 *   <li>팝업이 이미 여러 건 켜져 있음 — 사용자가 모달을 연달아 닫게 됩니다</li>
 * </ul>
 * 전부 오류가 아니라 정상 동작이라 상태 코드로는 전달할 수 없습니다. 그래서
 * {@code warnings} 에 실어 보냅니다({@code AdminAnnouncementResponse} 참고).
 */
@Service
@RequiredArgsConstructor
public class AdminAnnouncementService {

    /** 이 수를 넘겨 동시에 뜨면 경고합니다. 2건이면 이미 "연달아 닫기" 가 시작됩니다. */
    private static final int POPUP_CROWD_THRESHOLD = 2;

    private final AnnouncementRepository announcementRepository;

    @PersistenceContext
    private EntityManager em;

    /**
     * 관리 화면 목록. 임시저장·보관까지 전부 보입니다.
     *
     * <p>{@code pageable} 의 정렬은 버립니다 — 쿼리가 {@code id DESC} 로 고정하고 있고,
     * Spring Data 는 클라이언트의 {@code sort} 를 검증 없이 뒤에 붙이기 때문입니다.
     */
    @Transactional(readOnly = true)
    public Page<AdminAnnouncementResponse> search(AnnouncementStatus status, AnnouncementType type,
                                                  Boolean popup, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return announcementRepository.search(status, type, popup, unsorted)
                .map(AdminAnnouncementResponse::of);
    }

    /** 등록. {@code status} 를 생략하면 임시저장, {@code PUBLISHED} 면 바로 발행됩니다. */
    @Transactional
    public AdminAnnouncementResponse create(CreateAnnouncementRequest request, Long adminId) {
        Announcement announcement = Announcement.create(
                request.type(),
                request.title(),
                request.content(),
                request.status(),
                request.popupOrDefault(),
                request.startsOn(),
                request.endsOn(),
                adminId);

        return respond(announcementRepository.save(announcement));
    }

    /**
     * 수정. 보낸 필드만 반영합니다.
     *
     * <p>상태는 바꾸지 않습니다 — 발행·보관은 전이 규칙을 타야 해서 전용 경로로만 갑니다.
     */
    @Transactional
    public AdminAnnouncementResponse update(Long announcementId, UpdateAnnouncementRequest request) {
        Announcement announcement = load(announcementId);

        if (request.type() != null) {
            announcement.changeType(request.type());
        }
        if (request.title() != null) {
            announcement.changeTitle(request.title());
        }
        if (request.content() != null) {
            announcement.changeContent(request.content());
        }
        if (request.isPopup() != null) {
            announcement.changePopup(request.isPopup());
        }
        if (request.touchesPeriod()) {
            announcement.changePeriod(
                    resolvePeriodBound(request.startsOn(), announcement.getStartsOn(), request),
                    resolvePeriodBound(request.endsOn(), announcement.getEndsOn(), request));
        }

        return respond(announcement);
    }

    /** 발행. 이미 발행 중이면 {@code INVALID_STATE_TRANSITION}(400)입니다. */
    @Transactional
    public AdminAnnouncementResponse publish(Long announcementId) {
        Announcement announcement = load(announcementId);
        announcement.publish();
        return respond(announcement);
    }

    /**
     * 보관. 목록·팝업에서 빠집니다.
     *
     * <p>팝업 설정은 끄지 않습니다 — 되살릴 때 원래대로 돌아오는 편이 낫습니다.
     * 그래서 보관 응답에도 "팝업인데 안 뜬다" 경고가 함께 나갑니다. 의도된 상태입니다.
     */
    @Transactional
    public AdminAnnouncementResponse archive(Long announcementId) {
        Announcement announcement = load(announcementId);
        announcement.archive();
        return respond(announcement);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code clear_period} 를 반영해 기간 한쪽 값을 정합니다.
     *
     * <p>비우기를 요청했으면 기존 값을 버리고 이번에 보낸 값만 씁니다
     * ({@code UpdateAnnouncementRequest} 의 조합 표). 아니면 {@code null} 은 "그대로" 입니다.
     */
    private static LocalDate resolvePeriodBound(LocalDate requested, LocalDate current,
                                                UpdateAnnouncementRequest request) {
        if (request.shouldClearPeriod()) {
            return requested;
        }
        return requested != null ? requested : current;
    }

    /**
     * 응답을 만듭니다. <b>{@code flush} + {@code refresh} 가 필요합니다.</b>
     *
     * <p>{@code updated_at} 은 BEFORE UPDATE 트리거({@code trg_announcements_updated_at})가
     * 바꾸고, 엔티티는 그 컬럼을 UPDATE 시점에 다시 읽지 않습니다
     * ({@code BaseTimeEntity} 의 설명 — 낙관적 잠금을 지키기 위해 일부러 그렇게 둡니다).
     * 그래서 refresh 없이 응답을 만들면 <b>방금 수정했는데 {@code updated_at} 은 예전 값</b>이
     * 나갑니다. 관리 화면이 그 값으로 "마지막 수정" 을 보여 주면 조작이 안 먹은 것처럼 보입니다.
     *
     * <p>INSERT 직후에도 필요합니다 — {@code created_at}·{@code updated_at} 이
     * 컬럼 DEFAULT 로 채워지기 때문입니다.
     */
    private AdminAnnouncementResponse respond(Announcement announcement) {
        em.flush();
        em.refresh(announcement);
        return AdminAnnouncementResponse.of(announcement, warningsFor(announcement));
    }

    /**
     * 놓치면 조작이 조용히 무효가 되는 상황들.
     *
     * <p><b>전부 모아서 돌려줍니다.</b> 먼저 걸린 하나만 돌려주면 나머지는 사라지고,
     * 관리자는 경고를 하나 해결한 뒤에야 다음 경고를 보게 됩니다.
     */
    private List<String> warningsFor(Announcement announcement) {
        LocalDate today = LocalDate.now();
        List<String> warnings = new ArrayList<>();

        if (announcement.isPopup() && announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            warnings.add("팝업으로 지정했지만 상태가 " + announcement.getStatus()
                    + " 라 뜨지 않습니다. 발행해야 보입니다.");
        }

        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            LocalDate endsOn = announcement.getEndsOn();
            LocalDate startsOn = announcement.getStartsOn();

            if (endsOn != null && endsOn.isBefore(today)) {
                warnings.add("노출 종료일(" + endsOn + ")이 지났습니다. 발행 상태지만 목록·팝업에"
                        + " 나오지 않습니다 — 다시 노출하려면 종료일을 늘리거나 비우세요.");
            } else if (startsOn != null && startsOn.isAfter(today)) {
                warnings.add("노출 시작일이 " + startsOn + " 입니다. 그때까지는 보이지 않습니다(예약).");
            }
        }

        // ★ 이 공지가 지금 뜨는 팝업인 경우에만 셉니다. 저장 뒤라 자기 자신도 포함됩니다.
        if (announcement.isPopupOn(today)) {
            int crowd = announcementRepository.findPopups(today).size();
            if (crowd >= POPUP_CROWD_THRESHOLD) {
                warnings.add("지금 동시에 뜨는 팝업이 " + crowd + "건입니다. 사용자는 앱을 열 때"
                        + " " + crowd + "개를 연달아 닫아야 합니다.");
            }
        }

        return warnings;
    }

    private Announcement load(Long announcementId) {
        return announcementRepository.findById(announcementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }
}
