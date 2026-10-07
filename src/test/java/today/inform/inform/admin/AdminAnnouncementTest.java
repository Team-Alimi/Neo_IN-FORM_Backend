package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.announcement.dto.request.CreateAnnouncementRequest;
import today.inform.inform.admin.announcement.dto.request.UpdateAnnouncementRequest;
import today.inform.inform.admin.announcement.dto.response.AdminAnnouncementResponse;
import today.inform.inform.admin.announcement.service.AdminAnnouncementService;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;
import today.inform.inform.announcement.service.AnnouncementQueryService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.support.FakeFileStorage;
import today.inform.inform.support.IntegrationTest;

/**
 * 서비스 공지 관리.
 *
 * <p><b>여기서 가장 중요한 것은 경고입니다.</b> 공지는 저장이 성공하고 관리 화면에도 제대로
 * 보이는데 <b>사용자에게는 아무것도 안 뜨는</b> 조합이 여럿 있습니다 —
 * 팝업인데 임시저장, 종료일이 지난 발행, 시작일이 미래인 발행.
 * 전부 오류가 아니라서 상태 코드로는 알릴 수 없고, 경고를 빠뜨리면 운영자는
 * "분명히 켰는데 안 뜬다" 를 혼자 추적하게 됩니다.
 *
 * <p>두 번째는 <b>기간 비우기</b>입니다. {@code null} 이 "그대로 두기" 라서 날짜만으로는
 * 비울 수 없는데, 종료일을 잘못 넣은 공지는 목록·상세·팝업에서 통째로 사라집니다.
 * {@code clear_period} 가 없으면 되살릴 방법이 아예 없습니다.
 */
@Transactional
@Import(FakeFileStorage.Config.class)
class AdminAnnouncementTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    /** {@link FakeFileStorage} 가 우리 것으로 인정하는 모양의 주소. */
    private static final String IMAGE_URL = FakeFileStorage.BASE_URL + "2026/10/popup.png";
    private static final String OTHER_IMAGE_URL = FakeFileStorage.BASE_URL + "2026/10/popup-v2.png";

    @Autowired
    private AdminAnnouncementService adminService;

    @Autowired
    private FakeFileStorage storage;

    @Autowired
    private AnnouncementQueryService userService;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // 등록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("상태를 생략하면 임시저장이다 — 쓰다 만 공지가 사용자에게 새지 않는다")
    void createDefaultsToDraft() {
        AdminAnnouncementResponse created = create("기본 상태", null, null, null, null);

        assertThat(created.status()).isEqualTo(AnnouncementStatus.DRAFT);
        assertThat(created.publishedAt())
                .as("임시저장에는 발행 시각이 없습니다")
                .isNull();
        em.flush();
        assertThat(userService.list(PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("PUBLISHED 로 등록하면 바로 발행된다 — 쓰고 바로 올리는 경우에 호출을 두 번 하지 않아도 된다")
    void createCanPublishImmediately() {
        AdminAnnouncementResponse created =
                create("바로 발행", AnnouncementStatus.PUBLISHED, null, null, null);

        assertThat(created.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(created.publishedAt())
                .as("ck_ann_published 가 PUBLISHED + published_at NULL 을 거부합니다")
                .isNotNull();
    }

    @Test
    @DisplayName("ARCHIVED 로는 등록할 수 없다 — 한 번도 알리지 않은 \"지난 안내\" 는 뜻이 없다")
    void createCannotStartArchived() {
        assertThatThrownBy(() -> create("보관으로 시작", AnnouncementStatus.ARCHIVED, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("★ 공백만 보낸 본문은 거부된다 — 제목만 있는 팝업이 뜨는 것을 막는다")
    void blankContentIsRejected() {
        assertThatThrownBy(() -> adminService.create(new CreateAnnouncementRequest(
                AnnouncementType.GENERAL, "제목만 있음", "   ", null,
                AnnouncementStatus.PUBLISHED, true, null, null), null))
                .as("content 가 NOT NULL 이라 빈 문자열은 DB 를 그냥 통과합니다")
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 400 이다")
    void invalidPeriodIsRejected() {
        assertThatThrownBy(() -> create("거꾸로 기간", null, null,
                TODAY.plusDays(5), TODAY.plusDays(1)))
                .as("ck_ann_period 가 최종적으로 막지만 거기까지 가면 23514 로 뭉뚱그려집니다")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 상태 전이
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 보관했다 다시 발행해도 최초 발행 시각이 유지된다 — 목록 순서가 흔들리지 않는다")
    void republishKeepsFirstPublishedAt() {
        Long id = create("되살릴 공지", AnnouncementStatus.PUBLISHED, null, null, null).id();
        OffsetDateTime firstPublishedAt = adminService.archive(id).publishedAt();

        AdminAnnouncementResponse republished = adminService.publish(id);

        assertThat(republished.publishedAt())
                .as("되살릴 때마다 시각이 바뀌면 \"잘못 내린 것을 되살렸을 뿐\" 인 공지가 맨 위로 옵니다")
                .isEqualTo(firstPublishedAt);
    }

    @Test
    @DisplayName("이미 발행 중인 공지를 다시 발행하면 400 이다 — 조용히 통과시키면 버튼 결과를 구분할 수 없다")
    void publishingTwiceIsRejected() {
        Long id = create("두 번 발행", AnnouncementStatus.PUBLISHED, null, null, null).id();

        assertThatThrownBy(() -> adminService.publish(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("보관하면 사용자 목록에서 빠진다")
    void archiveHidesFromUsers() {
        Long id = create("내릴 공지", AnnouncementStatus.PUBLISHED, null, null, null).id();
        em.flush();
        assertThat(userService.list(PageRequest.of(0, 20)).getContent()).hasSize(1);

        adminService.archive(id);
        em.flush();

        assertThat(userService.list(PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("수정은 상태를 바꾸지 않는다 — 발행·보관은 전용 경로로만 간다")
    void updateDoesNotChangeStatus() {
        Long id = create("상태 유지", AnnouncementStatus.PUBLISHED, null, null, null).id();

        AdminAnnouncementResponse updated = adminService.update(id, patch().title("제목만 고침").req());

        assertThat(updated.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(updated.title()).isEqualTo("제목만 고침");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 경고 — 조용히 아무 일도 하지 않는 조합들
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 팝업을 켰지만 임시저장이면 경고한다 — 저장은 성공하고 화면에도 ON 으로 보인다")
    void popupOnDraftWarns() {
        AdminAnnouncementResponse created = create("팝업인데 임시저장", null, true, null, null);

        assertThat(created.warnings())
                .as("경고가 없으면 운영자는 \"분명히 켰는데 안 뜬다\" 를 혼자 추적합니다")
                .anySatisfy(warning -> assertThat(warning).contains("DRAFT"));
    }

    @Test
    @DisplayName("★ 종료일이 지난 공지를 발행하면 경고한다 — 상태는 PUBLISHED 인데 목록에 없다")
    void publishingExpiredWarns() {
        Long id = create("지난 안내", null, false, null, TODAY.minusDays(1)).id();

        AdminAnnouncementResponse published = adminService.publish(id);

        assertThat(published.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(published.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("종료일"));
        em.flush();
        assertThat(userService.list(PageRequest.of(0, 20)).getContent())
                .as("경고가 가리키는 사실 — 발행했지만 사용자에게는 없습니다")
                .isEmpty();
    }

    @Test
    @DisplayName("시작일이 미래면 예약이라고 알려 준다 — 종료일 지남과 모양이 같아서 구분이 필요하다")
    void scheduledPublishWarnsAsReservation() {
        AdminAnnouncementResponse created = create("다음 주 점검", AnnouncementStatus.PUBLISHED,
                false, TODAY.plusDays(7), null);

        assertThat(created.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("예약"));
    }

    @Test
    @DisplayName("★ 팝업이 동시에 여러 건 뜨면 건수를 알려 준다 — 사용자가 모달을 연달아 닫게 된다")
    void crowdedPopupsWarnWithCount() {
        create("팝업 1", AnnouncementStatus.PUBLISHED, true, null, null);
        AdminAnnouncementResponse second =
                create("팝업 2", AnnouncementStatus.PUBLISHED, true, null, null);

        assertThat(second.warnings())
                .as("상한으로 자르지 않는 대신 켜는 쪽에 알려 줍니다")
                .anySatisfy(warning -> assertThat(warning).contains("2건"));
    }

    @Test
    @DisplayName("정상적인 팝업 하나에는 경고가 없다 — 경고가 늘 붙으면 아무도 안 읽는다")
    void healthyPopupHasNoWarning() {
        AdminAnnouncementResponse created =
                create("정상 팝업", AnnouncementStatus.PUBLISHED, true, TODAY, TODAY.plusDays(7));

        assertThat(created.warnings()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 기간 비우기
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ clear_period 로 기간을 비울 수 있다 — 이게 없으면 잘못 넣은 종료일을 되살릴 방법이 없다")
    void clearPeriodEmptiesBothBounds() {
        Long id = create("기간 있는 공지", AnnouncementStatus.PUBLISHED, true,
                TODAY.minusDays(3), TODAY.minusDays(1)).id();
        em.flush();
        assertThat(userService.popups()).as("종료일이 지나 안 뜹니다").isEmpty();

        AdminAnnouncementResponse cleared = adminService.update(id, patch().clearPeriod().req());

        assertThat(cleared.startsOn()).isNull();
        assertThat(cleared.endsOn()).isNull();
        em.flush();
        assertThat(userService.popups())
                .as("기간을 비우면 다시 뜹니다")
                .hasSize(1);
    }

    @Test
    @DisplayName("clear_period + 한쪽 값이면 한쪽만 남는다")
    void clearPeriodKeepsValuesSentWithIt() {
        Long id = create("양쪽 있는 공지", null, false, TODAY.minusDays(3), TODAY.plusDays(3)).id();

        AdminAnnouncementResponse updated =
                adminService.update(id, patch().clearPeriod().startsOn(TODAY).req());

        assertThat(updated.startsOn()).isEqualTo(TODAY);
        assertThat(updated.endsOn())
                .as("비운 뒤 이번에 보낸 값만 적용됩니다")
                .isNull();
    }

    @Test
    @DisplayName("clear_period 없이 한쪽만 보내면 나머지는 유지된다")
    void partialPeriodUpdateKeepsTheOtherBound() {
        Long id = create("종료일만 미룰 공지", null, false, TODAY.minusDays(3), TODAY.plusDays(3)).id();

        AdminAnnouncementResponse updated =
                adminService.update(id, patch().endsOn(TODAY.plusDays(30)).req());

        assertThat(updated.startsOn())
                .as("null 은 \"그대로 두기\" 입니다")
                .isEqualTo(TODAY.minusDays(3));
        assertThat(updated.endsOn()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    @DisplayName("기간 필드를 아예 안 보내면 기간을 건드리지 않는다")
    void untouchedPeriodStays() {
        Long id = create("기간 유지", null, false, TODAY.minusDays(1), TODAY.plusDays(1)).id();

        AdminAnnouncementResponse updated = adminService.update(id, patch().title("제목만").req());

        assertThat(updated.startsOn()).isEqualTo(TODAY.minusDays(1));
        assertThat(updated.endsOn()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    @DisplayName("팝업은 false 를 보내야 꺼진다 — null 은 \"그대로\" 다")
    void popupNeedsExplicitFalseToTurnOff() {
        Long id = create("팝업 켜짐", AnnouncementStatus.PUBLISHED, true, null, null).id();

        assertThat(adminService.update(id, patch().title("제목만").req()).isPopup()).isTrue();
        assertThat(adminService.update(id, patch().popup(false).req()).isPopup()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 대표 이미지
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("등록할 때 대표 이미지를 붙일 수 있다")
    void createCanAttachImage() {
        AdminAnnouncementResponse created =
                create("이미지 공지", AnnouncementStatus.PUBLISHED, true, null, null, IMAGE_URL);

        assertThat(created.imageUrl()).isEqualTo(IMAGE_URL);
        em.flush();
        assertThat(userService.popups())
                .singleElement()
                .satisfies(popup -> assertThat(popup.imageUrl()).isEqualTo(IMAGE_URL));
    }

    @Test
    @DisplayName("★ 우리 스토리지 주소가 아니면 거부한다 — 받아 두면 삭제 보호가 조용히 안 걸린다")
    void foreignImageUrlIsRejected() {
        assertThatThrownBy(() -> create("남의 이미지", null, false, null, null,
                "https://example.com/someone-elses.png"))
                .as("그 사이트가 이미지를 내리면 팝업이 깨지는데 우리 쪽엔 아무 오류도 안 남습니다")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("★ 이미지를 교체해도 옛 객체는 스토리지에 남는다 — 다른 공지가 쓰고 있을 수 있다")
    void replacingImageKeepsTheOldObject() {
        Long id = create("교체할 공지", AnnouncementStatus.PUBLISHED, true, null, null, IMAGE_URL).id();

        AdminAnnouncementResponse updated =
                adminService.update(id, patch().imageUrl(OTHER_IMAGE_URL).req());

        assertThat(updated.imageUrl()).isEqualTo(OTHER_IMAGE_URL);
        assertThat(storage.deletedKeys())
                .as("S3 삭제는 되돌릴 수 없습니다. 치우는 건 DELETE /admin/files 가 할 일입니다")
                .isEmpty();
    }

    @Test
    @DisplayName("★ clear_image 로 이미지를 제거한다 — null 은 \"그대로\" 라 이것 없이는 못 지운다")
    void clearImageRemovesIt() {
        Long id = create("이미지 지울 공지", AnnouncementStatus.PUBLISHED, true, null, null, IMAGE_URL).id();

        AdminAnnouncementResponse cleared = adminService.update(id, patch().clearImage().req());

        assertThat(cleared.imageUrl()).isNull();
    }

    @Test
    @DisplayName("image_url 과 clear_image 를 함께 보내면 400 이다 — 뜻이 모순이다")
    void imageUrlAndClearImageTogetherIsRejected() {
        Long id = create("모순 요청", null, false, null, null, IMAGE_URL).id();

        assertThatThrownBy(() ->
                adminService.update(id, patch().imageUrl(OTHER_IMAGE_URL).clearImage().req()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("이미지 필드를 안 보내면 이미지를 건드리지 않는다")
    void untouchedImageStays() {
        Long id = create("이미지 유지", null, false, null, null, IMAGE_URL).id();

        AdminAnnouncementResponse updated = adminService.update(id, patch().title("제목만").req());

        assertThat(updated.imageUrl()).isEqualTo(IMAGE_URL);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 목록 · 응답
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("관리 목록에는 임시저장·보관까지 전부 나온다 — 안 나오면 다시 켤 수 없다")
    void adminListShowsEveryStatus() {
        create("임시저장", null, false, null, null);
        Long published = create("발행", AnnouncementStatus.PUBLISHED, false, null, null).id();
        adminService.archive(published);
        em.flush();

        assertThat(adminService.search(null, null, null, PageRequest.of(0, 20)).getContent())
                .hasSize(2)
                .extracting(AdminAnnouncementResponse::status)
                .containsExactlyInAnyOrder(AnnouncementStatus.DRAFT, AnnouncementStatus.ARCHIVED);
    }

    @Test
    @DisplayName("상태로 걸러진다")
    void adminListFiltersByStatus() {
        create("임시저장", null, false, null, null);
        create("발행", AnnouncementStatus.PUBLISHED, false, null, null);
        em.flush();

        assertThat(adminService.search(AnnouncementStatus.DRAFT, null, null, PageRequest.of(0, 20))
                .getContent())
                .singleElement()
                .extracting(AdminAnnouncementResponse::title)
                .isEqualTo("임시저장");
    }

    @Test
    @DisplayName("★ 수정 응답의 updated_at 이 갱신돼 있다 — refresh 를 빠뜨리면 예전 값이 나간다")
    void updatedAtIsFreshInResponse() {
        AdminAnnouncementResponse created = create("수정될 공지", null, false, null, null);

        AdminAnnouncementResponse updated =
                adminService.update(created.id(), patch().title("고친 제목").req());

        assertThat(updated.updatedAt())
                .as("트리거가 바꾸는 값이라 refresh 없이는 메모리의 낡은 값이 그대로 나갑니다")
                .isAfterOrEqualTo(created.updatedAt());
        assertThat(updated.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("없는 공지를 건드리면 404 다")
    void unknownAnnouncementIsNotFound() {
        assertThatThrownBy(() -> adminService.publish(99_999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AdminAnnouncementResponse create(String title, AnnouncementStatus status, Boolean popup,
                                             LocalDate startsOn, LocalDate endsOn) {
        return create(title, status, popup, startsOn, endsOn, null);
    }

    private AdminAnnouncementResponse create(String title, AnnouncementStatus status, Boolean popup,
                                             LocalDate startsOn, LocalDate endsOn, String imageUrl) {
        return adminService.create(new CreateAnnouncementRequest(
                AnnouncementType.GENERAL, title, title + " 본문", imageUrl,
                status, popup, startsOn, endsOn), null);
    }

    /**
     * PATCH 요청 빌더. 필드가 일곱 개라 생성자를 직접 부르면 호출마다
     * {@code null} 이 다섯 줄씩 붙어서, 무엇을 보내는 요청인지 읽기 어려워집니다.
     */
    private static Patch patch() {
        return new Patch();
    }

    private static final class Patch {
        private String title;
        private Boolean popup;
        private LocalDate startsOn;
        private LocalDate endsOn;
        private Boolean clearPeriod;
        private String imageUrl;
        private Boolean clearImage;

        Patch title(String value) {
            this.title = value;
            return this;
        }

        Patch popup(Boolean value) {
            this.popup = value;
            return this;
        }

        Patch startsOn(LocalDate value) {
            this.startsOn = value;
            return this;
        }

        Patch endsOn(LocalDate value) {
            this.endsOn = value;
            return this;
        }

        Patch clearPeriod() {
            this.clearPeriod = true;
            return this;
        }

        Patch imageUrl(String value) {
            this.imageUrl = value;
            return this;
        }

        Patch clearImage() {
            this.clearImage = true;
            return this;
        }

        UpdateAnnouncementRequest req() {
            return new UpdateAnnouncementRequest(null, title, null, imageUrl, clearImage,
                    popup, startsOn, endsOn, clearPeriod);
        }
    }
}
