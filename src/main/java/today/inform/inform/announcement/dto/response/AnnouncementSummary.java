package today.inform.inform.announcement.dto.response;

import java.time.OffsetDateTime;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementType;

/**
 * 사용자 공지 목록의 한 줄.
 *
 * <p><b>{@code content} 를 싣지 않습니다.</b> 목록은 제목만 보여 주는데 본문까지 실으면
 * 공지가 쌓일수록 응답이 선형으로 커집니다. 본문은 상세에서 받습니다.
 *
 * <p><b>{@code is_popup} 을 내보내지 않습니다.</b> 팝업 여부는 목록 표시와 무관합니다 —
 * 내보내면 화면이 그걸로 배지를 달거나 위로 올려서, V16 에서 없앤 "상단 고정" 이
 * 프론트에 다시 생깁니다. 지금 띄울 팝업은 {@code GET /announcements/popup} 이 알려 줍니다.
 *
 * <p><b>{@code status} 도 내보내지 않습니다.</b> 이 목록에 나오는 것은 전부 발행 중이라
 * 값이 하나뿐이고, 내보내면 "ARCHIVED 도 올 수 있나" 를 매번 묻게 됩니다.
 */
public record AnnouncementSummary(
        Long id,
        AnnouncementType type,
        String title,
        OffsetDateTime publishedAt) {

    public static AnnouncementSummary from(Announcement announcement) {
        return new AnnouncementSummary(
                announcement.getId(),
                announcement.getType(),
                announcement.getTitle(),
                announcement.getPublishedAt());
    }
}
