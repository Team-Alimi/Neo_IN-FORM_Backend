package today.inform.inform.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import today.inform.inform.global.entity.BaseTimeEntity;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 서비스 공지 — 운영팀이 앱 사용자에게 직접 하는 안내.
 *
 * <p>수집해 온 학교·동아리 글({@code articles})과는 <b>별개 테이블</b>입니다.
 * 이름이 비슷해 헷갈리므로 {@link AnnouncementType} 의 설명도 함께 보세요.
 *
 * <h2>★ {@code published_at} 은 이 엔티티가 소유합니다</h2>
 * {@code articles.published_at} 은 트리거(V6)가 채우지만 <b>여기는 트리거가 없습니다.</b>
 * {@code announcements} 를 쓰는 경로가 관리자 API 하나뿐이라, 앱이 찍어도 "앱을 거치지 않는
 * 발행 경로" 가 생기지 않기 때문입니다(크롤러는 이 테이블을 아예 모릅니다).
 *
 * <p>대신 {@code ck_ann_published} 가 불변식을 지킵니다 —
 * 상태가 PUBLISHED 인데 {@code published_at} 이 비어 있으면 DB 가 거부합니다.
 * 그래서 {@link #publish()} 를 거치지 않고 상태만 바꾸는 코드는 저장에서 터집니다.
 *
 * <p><b>최초 발행 시각은 유지됩니다.</b> 보관했다 다시 올려도 덮어쓰지 않습니다 —
 * 목록이 {@code published_at DESC} 라, 되살릴 때마다 시각이 바뀌면 "잘못 내린 것을
 * 되살렸을 뿐" 인 공지가 맨 위로 올라옵니다. 새 안내로 알리고 싶으면 새로 작성하는 게 맞습니다.
 *
 * <h2>★ {@code isPopup} 은 목록 노출과 무관합니다</h2>
 * 원래 이 컬럼은 {@code is_pinned}(목록 상단 고정)였고 V16 에서 이름을 바꿨습니다.
 * <b>정렬에 쓰지 마세요.</b> 팝업으로 띄울지만 정하고, 목록에서는 다른 공지와 똑같이
 * 최신순으로 섞입니다. 여러 건이 동시에 켜질 수 있습니다.
 */
@Getter
@Entity
@Table(name = "announcements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseTimeEntity {

    public static final int TITLE_MAX_LENGTH = 500;

    /** {@code attachments.file_url} 과 같은 길이. 같은 스토리지가 만든 주소라 기준을 맞춥니다. */
    public static final int IMAGE_URL_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AnnouncementType type;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /**
     * 대표 이미지 한 장. {@code null} 이면 없습니다.
     *
     * <p><b>우리 스토리지 주소만 들어갑니다.</b> 그 판정은 서비스가 합니다
     * ({@code FileStorage.objectKeyOf}) — 엔티티가 스토리지를 알면 안 되고,
     * 무엇보다 남의 주소를 받으면 <b>삭제 보호가 조용히 적용되지 않습니다.</b>
     *
     * <p><b>{@code attachments} 를 쓰지 않는 이유</b> — 그 테이블은 {@code article_id} 가
     * {@code NOT NULL} 이라 공지를 넣을 자리가 없습니다. 필요한 것도 한 장이라
     * 목록 구조가 필요 없습니다.
     *
     * <p>⚠ <b>이 값이 가리키는 객체는 {@code DELETE /admin/files} 로부터 보호돼야 합니다.</b>
     * 그 API 는 "공지에 연결됐는가" 를 {@code AttachmentQueryRepository.findLinkedUrls} 로
     * 판정하는데, 거기에 이 컬럼이 들어가 있어야 합니다. 빠지면 운영 중인 팝업의 이미지를
     * 지울 수 있고 — 오류도 404 도 없이 <b>이미지만 안 뜹니다.</b>
     */
    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnnouncementStatus status;

    /** 앱 진입 시 팝업으로 띄울지. <b>목록 정렬과 무관합니다.</b> */
    @Column(name = "is_popup", nullable = false)
    private boolean popup;

    /** 최초 발행 시각. {@link #publish()} 가 비어 있을 때만 찍습니다. */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /**
     * 노출 시작일. {@code null} 이면 "발행 즉시".
     *
     * <p><b>날짜 단위입니다.</b> 운영 공지에 분 단위가 필요한 적이 없고,
     * timestamptz 로 두면 관리자가 넣은 시각이 어느 시간대인지를 매번 따져야 합니다.
     */
    @Column(name = "starts_on")
    private LocalDate startsOn;

    /** 노출 종료일. {@code null} 이면 "계속". 이 날짜까지 포함입니다(경계 포함). */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    /**
     * 작성한 관리자. 연관관계로 매핑하지 않는 이유는 {@code Article.createdBy} 와 같습니다.
     * ({@code ON DELETE SET NULL} 이라 NULL 일 수 있습니다 = 탈퇴한 관리자)
     */
    @Column(name = "created_by")
    private Long createdBy;

    // ─────────────────────────────────────────────────────────────────────────
    // 생성
    // ─────────────────────────────────────────────────────────────────────────

    private Announcement(AnnouncementType type, String title, String content, String imageUrl,
                         AnnouncementStatus status, boolean popup,
                         LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        this.type = requireType(type);
        this.title = requireTitle(title);
        this.content = requireContent(content);
        this.imageUrl = normalizeImageUrl(imageUrl);
        this.status = status;
        this.popup = popup;
        validatePeriod(startsOn, endsOn);
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
        if (status == AnnouncementStatus.PUBLISHED) {
            this.publishedAt = OffsetDateTime.now();
        }
    }

    /**
     * 작성. 초기 상태는 DRAFT 또는 PUBLISHED 입니다.
     *
     * <p><b>ARCHIVED 로는 만들 수 없습니다.</b> 한 번도 노출되지 않은 "지난 안내" 는
     * 뜻이 없고, 쓰다 만 것을 접는 자리는 DRAFT 입니다.
     *
     * <p>PUBLISHED 로 바로 만드는 것은 {@link #publish()} 를 거치지 않지만 전이 규칙을
     * 어기는 게 아닙니다 — 생성이지 전이가 아닙니다. {@code published_at} 은 여기서 찍습니다.
     */
    public static Announcement create(AnnouncementType type, String title, String content,
                                      String imageUrl,
                                      AnnouncementStatus initialStatus, boolean popup,
                                      LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        AnnouncementStatus status = initialStatus == null ? AnnouncementStatus.DRAFT : initialStatus;
        if (status == AnnouncementStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "보관 상태로는 작성할 수 없습니다. 임시저장(DRAFT) 또는 발행(PUBLISHED)만 됩니다.");
        }
        return new Announcement(type, title, content, imageUrl,
                status, popup, startsOn, endsOn, createdBy);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────────────────────────────────────

    /** 유형 변경. 배지 색만 바뀝니다 — 동작에는 영향이 없습니다. */
    public void changeType(AnnouncementType newType) {
        this.type = requireType(newType);
    }

    public void changeTitle(String newTitle) {
        this.title = requireTitle(newTitle);
    }

    public void changeContent(String newContent) {
        this.content = requireContent(newContent);
    }

    /**
     * 대표 이미지 교체 · 제거. {@code null} 이면 제거입니다.
     *
     * <p><b>옛 객체를 스토리지에서 지우지 않습니다.</b> 공지 첨부와 같은 규칙입니다
     * ({@code AdminArticleWriteService} 도 교체 시 지우지 않고, 영구삭제·병합에서만 지웁니다).
     * 서버가 교체 시 지우면 <b>같은 이미지를 다른 공지가 쓰고 있을 때 그쪽이 깨지고</b>,
     * S3 삭제는 되돌릴 수 없습니다. 쌓인 것은 {@code DELETE /admin/files} 로 치웁니다 —
     * 그쪽은 아직 아무 공지에도 안 붙은 것만 지웁니다.
     */
    public void changeImageUrl(String newImageUrl) {
        this.imageUrl = normalizeImageUrl(newImageUrl);
    }

    /**
     * 팝업 켜고 끄기.
     *
     * <p>발행 전에 켜 두어도 됩니다 — 팝업 조회는 발행 상태를 함께 보므로
     * 임시저장 상태에서는 뜨지 않습니다.
     */
    public void changePopup(boolean value) {
        this.popup = value;
    }

    /**
     * 노출 기간 교체. 두 값을 <b>함께</b> 바꿉니다.
     *
     * <p>하나씩 바꾸게 두면 {@code ck_ann_period}(시작일 ≤ 종료일)을 중간 상태에서 어기게 되고,
     * 그걸 피하려고 순서를 따지는 코드가 호출하는 쪽에 생깁니다.
     *
     * <p>둘 다 {@code null} 이면 "기간 제한 없음" 입니다 — 지우는 경로이기도 합니다.
     */
    public void changePeriod(LocalDate newStartsOn, LocalDate newEndsOn) {
        validatePeriod(newStartsOn, newEndsOn);
        this.startsOn = newStartsOn;
        this.endsOn = newEndsOn;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 상태
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 발행. {@code published_at} 이 비어 있을 때만 찍습니다({@link #publishedAt} 참고).
     *
     * <p>이미 발행 중이면 {@code INVALID_STATE_TRANSITION} 입니다 —
     * 조용히 통과시키면 호출한 쪽이 "방금 발행됐다" 와 "이미 발행돼 있었다" 를 구분할 수 없습니다.
     */
    public void publish() {
        transitionTo(AnnouncementStatus.PUBLISHED);
        if (publishedAt == null) {
            this.publishedAt = OffsetDateTime.now();
        }
    }

    /**
     * 보관. 목록과 팝업에서 빠집니다.
     *
     * <p><b>{@code published_at} 과 {@code is_popup} 은 그대로 둡니다.</b>
     * 되살릴 때 원래 설정으로 돌아오는 편이 낫고, 팝업을 끄는 것은 별도 조작입니다.
     */
    public void archive() {
        transitionTo(AnnouncementStatus.ARCHIVED);
    }

    /**
     * 지금({@code today} 기준) 사용자에게 보여야 하는지.
     *
     * <p>조회는 SQL 이 같은 판정을 하고({@code AnnouncementRepository}), 이쪽은 단건 처리와
     * 테스트가 씁니다. <b>{@code today} 를 받습니다</b> — 안에서 {@code LocalDate.now()} 를
     * 부르면 호출 시점에 따라 결과가 흔들리고, SQL 쪽과 시계가 달라집니다.
     */
    public boolean isVisibleOn(LocalDate today) {
        return status.isVisibleToUsers()
                && (startsOn == null || !startsOn.isAfter(today))
                && (endsOn == null || !endsOn.isBefore(today));
    }

    /** 지금 팝업으로 떠야 하는지. */
    public boolean isPopupOn(LocalDate today) {
        return popup && isVisibleOn(today);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void transitionTo(AnnouncementStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "현재 상태(" + status + ")에서 " + next + " 로는 바꿀 수 없습니다.");
        }
        this.status = next;
    }

    private static AnnouncementType requireType(AnnouncementType value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "공지 유형을 선택해 주세요.");
        }
        return value;
    }

    private static String requireTitle(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "제목을 입력해 주세요.");
        }
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "제목은 " + TITLE_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    /**
     * 본문은 앞뒤 공백만 걷어내고 그대로 둡니다.
     *
     * <p>공백만 보낸 경우를 막는 것이 핵심입니다 — {@code content} 가 {@code NOT NULL} 이라
     * 빈 문자열은 DB 를 통과하고, 그러면 <b>제목만 있고 내용이 없는 팝업</b>이 뜹니다.
     */
    private static String requireContent(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "내용을 입력해 주세요.");
        }
        return trimmed;
    }

    /**
     * 빈 문자열을 {@code null} 로 접고 길이만 봅니다.
     *
     * <p>빈 문자열을 그대로 두면 화면이 "이미지 있음" 으로 읽어 <b>깨진 이미지를 그립니다.</b>
     * 주소가 우리 스토리지인지는 여기서 보지 않습니다 — 서비스가 봅니다.
     */
    private static String normalizeImageUrl(String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > IMAGE_URL_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이미지 주소는 " + IMAGE_URL_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    /** {@code ck_ann_period} 와 같은 내용. 여기서 먼저 걸러 23514 대신 400 으로 돌려줍니다. */
    private static void validatePeriod(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn != null && endsOn != null && startsOn.isAfter(endsOn)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "노출 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
