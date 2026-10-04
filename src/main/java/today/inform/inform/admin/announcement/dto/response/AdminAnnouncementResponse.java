package today.inform.inform.admin.announcement.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;

/**
 * 관리자 공지 한 건. 사용자용 응답과 달리 <b>숨은 값을 전부</b> 내보냅니다.
 *
 * <p>목록도 이 모양을 씁니다 — {@code content} 까지 실립니다. 수정 화면이 목록에서 바로
 * 열리게 하려면 본문이 있어야 하고, 관리자 화면은 페이징되므로 한 번에 20건입니다.
 * 상세 엔드포인트를 따로 두지 않은 이유입니다.
 *
 * @param warnings 오류는 아니지만 <b>놓치면 조작이 아무 일도 하지 않는</b> 상황들.
 *                 없으면 빈 배열입니다.
 *                 <p><b>왜 목록인가</b> — 제공처·분류는 {@code warning} 하나였는데 여기서는
 *                 여러 개가 동시에 성립합니다("팝업인데 임시저장" + "종료일이 지남" 처럼).
 *                 하나만 돌려주면 나머지가 조용히 사라지고, 그게 바로 이 필드가 막으려는 것입니다
 */
public record AdminAnnouncementResponse(
        Long id,
        AnnouncementType type,
        String title,
        String content,
        AnnouncementStatus status,
        boolean isPopup,
        OffsetDateTime publishedAt,
        LocalDate startsOn,
        LocalDate endsOn,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<String> warnings) {

    public static AdminAnnouncementResponse of(Announcement announcement, List<String> warnings) {
        return new AdminAnnouncementResponse(
                announcement.getId(),
                announcement.getType(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getStatus(),
                announcement.isPopup(),
                announcement.getPublishedAt(),
                announcement.getStartsOn(),
                announcement.getEndsOn(),
                announcement.getCreatedBy(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                warnings);
    }

    /**
     * 목록용. <b>경고를 계산하지 않습니다.</b>
     *
     * <p>목록에는 {@code status} 와 {@code is_popup} 이 그대로 나오므로 관리자가 표에서
     * 직접 봅니다. 행마다 경고 문장을 붙이면 화면이 경고로 가득 차서 정작 방금 한 조작의
     * 경고가 묻힙니다 — 경고는 쓰기 응답(등록·수정·발행·보관)에만 싣습니다.
     */
    public static AdminAnnouncementResponse of(Announcement announcement) {
        return of(announcement, List.of());
    }
}
