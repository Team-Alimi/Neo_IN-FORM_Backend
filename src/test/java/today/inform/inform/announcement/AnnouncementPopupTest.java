package today.inform.inform.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.announcement.dto.response.AnnouncementDetail;
import today.inform.inform.announcement.dto.response.AnnouncementSummary;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;
import today.inform.inform.announcement.repository.AnnouncementRepository;
import today.inform.inform.announcement.service.AnnouncementQueryService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.support.IntegrationTest;

/**
 * 사용자용 서비스 공지 — 노출 조건과 팝업.
 *
 * <p><b>여기서 가장 중요한 것은 "고정" 이 없다는 것입니다.</b>
 * V16 전까지 이 플래그는 {@code is_pinned}(목록 상단 고정)였습니다. 이름만 바꾸고 정렬이
 * 남아 있으면 팝업을 켤 때마다 목록 순서가 조용히 달라집니다 — 오류가 없어서
 * 화면을 눈으로 보기 전에는 드러나지 않습니다. {@link #popupDoesNotAffectListOrder()} 가 막습니다.
 *
 * <p>두 번째는 <b>경계 날짜</b>입니다. {@code starts_on}·{@code ends_on} 이 오늘과 같은 날에
 * 노출되는지는 "오늘 하루만 띄우는 점검 공지" 가 뜨는지와 같은 말입니다.
 * 부등호 하나로 하루가 통째로 사라집니다.
 */
@Transactional
class AnnouncementPopupTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private AnnouncementQueryService announcementService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // 팝업 조회 — 네 조건이 모두 맞아야 뜬다
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("팝업은 발행 + 팝업 지정 + 기간 안쪽이어야 뜬다")
    void popupNeedsEveryCondition() {
        Long shown = save("뜨는 팝업", AnnouncementStatus.PUBLISHED, true, null, null);
        save("임시저장인데 팝업", AnnouncementStatus.DRAFT, true, null, null);
        save("보관인데 팝업", AnnouncementStatus.ARCHIVED, true, null, null);
        save("발행이지만 팝업 아님", AnnouncementStatus.PUBLISHED, false, null, null);
        save("시작 전", AnnouncementStatus.PUBLISHED, true, TODAY.plusDays(1), null);
        save("종료됨", AnnouncementStatus.PUBLISHED, true, null, TODAY.minusDays(1));

        assertThat(announcementService.popups())
                .as("네 조건 중 하나라도 어긋나면 뜨지 않습니다")
                .extracting(AnnouncementDetail::id)
                .containsExactly(shown);
    }

    @Test
    @DisplayName("★ 경계 날짜는 포함이다 — 오늘 하루만 띄우는 공지가 뜬다")
    void periodBoundariesAreInclusive() {
        Long oneDay = save("오늘 하루", AnnouncementStatus.PUBLISHED, true, TODAY, TODAY);

        assertThat(announcementService.popups())
                .as("시작일 == 종료일 == 오늘. 부등호가 하나 어긋나면 이 공지는 영원히 안 뜹니다")
                .extracting(AnnouncementDetail::id)
                .containsExactly(oneDay);
    }

    @Test
    @DisplayName("팝업은 여러 건이 동시에 뜬다 — 정책이 그렇다")
    void multiplePopupsAreAllowed() {
        save("팝업 1", AnnouncementStatus.PUBLISHED, true, null, null);
        save("팝업 2", AnnouncementStatus.PUBLISHED, true, null, null);
        save("팝업 3", AnnouncementStatus.PUBLISHED, true, null, null);

        assertThat(announcementService.popups())
                .as("상한을 두면 관리자가 켠 팝업이 조용히 안 뜨는 경우가 생깁니다")
                .hasSize(3);
    }

    @Test
    @DisplayName("띄울 팝업이 없으면 빈 배열이다 — 404 가 아니다")
    void noPopupIsNotAnError() {
        save("팝업 아님", AnnouncementStatus.PUBLISHED, false, null, null);

        assertThat(announcementService.popups())
                .as("앱 진입마다 부르는 호출이라 오류로 다루면 클라이언트가 재시도합니다")
                .isEmpty();
    }

    @Test
    @DisplayName("팝업 응답에 본문이 들어 있다 — 띄우려고 상세를 한 번 더 부르지 않아도 된다")
    void popupCarriesContent() {
        save("점검 안내", AnnouncementStatus.PUBLISHED, true, null, null);

        assertThat(announcementService.popups())
                .singleElement()
                .satisfies(popup -> {
                    assertThat(popup.title()).isEqualTo("점검 안내");
                    assertThat(popup.content()).isNotBlank();
                    assertThat(popup.id())
                            .as("프론트가 \"다시 보지 않기\" 키로 씁니다")
                            .isNotNull();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 목록 — 고정 개념이 없다
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 팝업이 목록 순서를 바꾸지 않는다 — V16 에서 없앤 \"상단 고정\" 이 되살아나면 실패한다")
    void popupDoesNotAffectListOrder() {
        Long older = save("먼저 쓴 팝업", AnnouncementStatus.PUBLISHED, true, null, null);
        em.flush();
        Long newer = save("나중에 쓴 일반 공지", AnnouncementStatus.PUBLISHED, false, null, null);

        assertThat(announcementService.list(PageRequest.of(0, 20)).getContent())
                .as("팝업이 위로 올라가면 older 가 먼저 나옵니다. 최신순이어야 합니다")
                .extracting(AnnouncementSummary::id)
                .containsExactly(newer, older);
    }

    @Test
    @DisplayName("목록에는 발행 + 기간 안쪽만 나온다")
    void listShowsOnlyVisible() {
        Long shown = save("노출 중", AnnouncementStatus.PUBLISHED, false, null, null);
        save("임시저장", AnnouncementStatus.DRAFT, false, null, null);
        save("보관", AnnouncementStatus.ARCHIVED, false, null, null);
        save("예약", AnnouncementStatus.PUBLISHED, false, TODAY.plusDays(3), null);
        save("종료", AnnouncementStatus.PUBLISHED, false, null, TODAY.minusDays(3));

        assertThat(announcementService.list(PageRequest.of(0, 20)).getContent())
                .extracting(AnnouncementSummary::id)
                .containsExactly(shown);
    }

    @Test
    @DisplayName("목록에는 본문이 실리지 않는다 — 공지가 쌓일수록 응답이 커지는 것을 막는다")
    void listOmitsContent() {
        // AnnouncementSummary 에 content 필드가 아예 없습니다. 컴파일로 보장되는 사실이지만,
        // 나중에 "목록에서도 본문 쓰자" 가 되면 이 테스트가 그 변경을 드러냅니다.
        assertThat(AnnouncementSummary.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "type", "title", "publishedAt");
    }

    @Test
    @DisplayName("★ 목록에 is_popup 이 나가지 않는다 — 나가면 프론트가 고정을 다시 만든다")
    void listDoesNotExposePopupFlag() {
        assertThat(AnnouncementSummary.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .as("지금 띄울 팝업은 GET /announcements/popup 이 알려 줍니다")
                .doesNotContain("isPopup", "popup");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 상세 — 목록과 같은 조건
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 임시저장 공지는 번호를 알아도 열리지 않는다")
    void draftIsNotReachableById() {
        Long draft = save("아직 안 알린 공지", AnnouncementStatus.DRAFT, false, null, null);
        em.flush();

        assertThatThrownBy(() -> announcementService.detail(draft))
                .as("id 가 bigserial 이라 번호를 맞추는 것은 쉽습니다")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("★ 예약 공지도 상세로 새지 않는다 — 기간이 목록·상세 둘 다에 걸린다")
    void scheduledIsNotReachableById() {
        Long scheduled = save("다음 주 점검", AnnouncementStatus.PUBLISHED, false,
                TODAY.plusDays(7), null);
        em.flush();

        assertThatThrownBy(() -> announcementService.detail(scheduled))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("기간이 지난 공지의 상세는 404 다")
    void expiredIsNotReachableById() {
        Long expired = save("지난 안내", AnnouncementStatus.PUBLISHED, false, null, TODAY.minusDays(1));
        em.flush();

        assertThatThrownBy(() -> announcementService.detail(expired))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("노출 중인 공지의 상세는 본문까지 나온다")
    void visibleDetailIsReadable() {
        Long id = save("읽을 수 있는 공지", AnnouncementStatus.PUBLISHED, false, null, null);
        em.flush();

        AnnouncementDetail detail = announcementService.detail(id);
        assertThat(detail.title()).isEqualTo("읽을 수 있는 공지");
        assertThat(detail.content()).isNotBlank();
        assertThat(detail.publishedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 자바 판정과 SQL 판정이 같은지
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 엔티티의 노출 판정과 쿼리의 판정이 일치한다 — 한쪽만 고치면 여기서 걸린다")
    void javaAndSqlAgreeOnVisibility() {
        List<Announcement> all = List.of(
                newAnnouncement("둘 다 없음", AnnouncementStatus.PUBLISHED, true, null, null),
                newAnnouncement("오늘 시작", AnnouncementStatus.PUBLISHED, true, TODAY, null),
                newAnnouncement("오늘 종료", AnnouncementStatus.PUBLISHED, true, null, TODAY),
                newAnnouncement("내일 시작", AnnouncementStatus.PUBLISHED, true, TODAY.plusDays(1), null),
                newAnnouncement("어제 종료", AnnouncementStatus.PUBLISHED, true, null, TODAY.minusDays(1)),
                newAnnouncement("임시저장", AnnouncementStatus.DRAFT, true, null, null));
        all.forEach(announcementRepository::save);
        em.flush();

        List<Long> fromSql = announcementRepository.findPopups(TODAY).stream()
                .map(Announcement::getId)
                .toList();
        List<Long> fromJava = all.stream()
                .filter(announcement -> announcement.isPopupOn(TODAY))
                .map(Announcement::getId)
                .toList();

        assertThat(fromSql)
                .as("목록에는 있는데 상세는 404 같은 어긋남이 여기서 드러납니다")
                .containsExactlyInAnyOrderElementsOf(fromJava);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Long save(String title, AnnouncementStatus status, boolean popup,
                      LocalDate startsOn, LocalDate endsOn) {
        return announcementRepository
                .save(newAnnouncement(title, status, popup, startsOn, endsOn))
                .getId();
    }

    private static Announcement newAnnouncement(String title, AnnouncementStatus status,
                                                boolean popup, LocalDate startsOn, LocalDate endsOn) {
        Announcement announcement = Announcement.create(
                AnnouncementType.GENERAL, title, title + " 본문",
                status == AnnouncementStatus.ARCHIVED ? AnnouncementStatus.PUBLISHED : status,
                popup, startsOn, endsOn, null);
        if (status == AnnouncementStatus.ARCHIVED) {
            // 보관은 생성으로 만들 수 없습니다 — 발행한 뒤 내립니다.
            announcement.archive();
        }
        return announcement;
    }
}
