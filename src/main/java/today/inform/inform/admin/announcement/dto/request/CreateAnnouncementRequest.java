package today.inform.inform.admin.announcement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;

/**
 * 서비스 공지 등록.
 *
 * @param status   초기 상태. 생략하면 <b>임시저장(DRAFT)</b> 입니다.
 *                 {@code PUBLISHED} 로 보내면 바로 발행됩니다 — 쓰고 바로 올리는 경우가
 *                 대부분이라 등록 후 발행을 두 번 부르지 않아도 되게 열어 둡니다.
 *                 {@code ARCHIVED} 는 400 입니다
 * @param isPopup  앱 진입 시 팝업으로 띄울지. 생략하면 {@code false}.
 *                 <b>임시저장 상태에서 켜 두어도 됩니다</b> — 발행될 때부터 뜹니다
 * @param startsOn 노출 시작일. 생략하면 "발행 즉시". 미래로 두면 예약이 됩니다
 * @param endsOn   노출 종료일(이 날짜 포함). 생략하면 "계속"
 */
public record CreateAnnouncementRequest(
        @NotNull(message = "공지 유형을 선택해 주세요.")
        AnnouncementType type,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = Announcement.TITLE_MAX_LENGTH, message = "제목은 500자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "내용을 입력해 주세요.")
        String content,

        AnnouncementStatus status,

        Boolean isPopup,

        LocalDate startsOn,

        LocalDate endsOn) {

    /** 생략을 {@code false} 로 읽습니다. 팝업은 <b>명시해서 켜는</b> 것이 맞습니다. */
    public boolean popupOrDefault() {
        return Boolean.TRUE.equals(isPopup);
    }
}
