package today.inform.inform.announcement.dto.response;

import java.time.OffsetDateTime;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementType;

/**
 * 공지 본문. <b>상세 조회와 팝업 조회가 같은 모양을 씁니다.</b>
 *
 * <p>팝업을 따로 만들지 않은 이유 — 팝업이 띄울 것도 결국 제목과 본문입니다.
 * 모양을 나누면 프론트가 같은 내용을 두 번 그리게 되고, 한쪽에 필드를 더할 때
 * 다른 쪽을 빠뜨립니다.
 *
 * <p><b>{@code id} 가 팝업의 "다시 보지 않기" 키입니다.</b> 서버는 사용자가 무엇을 닫았는지
 * 저장하지 않습니다 — 기기별 설정이라 기기마다 다르고, 공지 하나당 사용자 수만큼 행이
 * 쌓이는 테이블을 만들 이유가 없습니다. 프론트가 이 번호를
 * {@code localStorage} 에 "언제까지 숨김" 과 함께 적어 두면 됩니다.
 *
 * <p>⚠ 그래서 <b>발행된 공지의 내용을 크게 고치면 안 됩니다.</b> 이미 닫은 사용자는
 * 같은 번호를 숨긴 상태라 바뀐 내용을 영원히 못 봅니다. 오타 수정은 괜찮지만
 * 내용이 달라지면 새로 작성해야 합니다.
 *
 * @param imageUrl 대표 이미지. 없으면 <b>필드 자체가 빠집니다</b>
 *                 ({@code default-property-inclusion: non_null}).
 *                 {@code null} 검사 대신 필드 존재 여부로 분기해도 됩니다
 */
public record AnnouncementDetail(
        Long id,
        AnnouncementType type,
        String title,
        String content,
        String imageUrl,
        OffsetDateTime publishedAt) {

    public static AnnouncementDetail from(Announcement announcement) {
        return new AnnouncementDetail(
                announcement.getId(),
                announcement.getType(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getImageUrl(),
                announcement.getPublishedAt());
    }
}
