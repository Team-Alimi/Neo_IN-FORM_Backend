package today.inform.inform.admin.announcement.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import today.inform.inform.announcement.entity.Announcement;
import today.inform.inform.announcement.entity.AnnouncementType;

/**
 * 서비스 공지 수정. <b>부분 수정입니다</b> — 보낸 필드만 반영하고 {@code null} 은 그대로 둡니다.
 * 그래서 {@code @NotBlank} 가 없습니다. 값의 내용 검사는 엔티티가 합니다.
 *
 * <p><b>상태는 여기서 바꾸지 않습니다.</b> 전이 규칙을 타야 하므로 발행·보관 전용
 * 엔드포인트로만 바뀝니다. 수정은 어느 상태에서나 됩니다 — 발행된 공지의 오타도 고쳐야 합니다.
 *
 * <h2>★ 기간을 비우는 방법</h2>
 * {@code null} 이 "그대로 두기" 라서 날짜만으로는 "비워 달라" 를 표현할 수 없습니다
 * (목록 필드는 빈 배열로 구분되지만 날짜에는 그런 값이 없습니다).
 * 그래서 {@code clearPeriod} 를 둡니다 — <b>기존 기간을 먼저 지우고</b>, 같은 요청의
 * {@code startsOn}·{@code endsOn} 을 그 위에 적용합니다.
 *
 * <table>
 *   <caption>조합</caption>
 *   <tr><th>보낸 것</th><th>결과</th></tr>
 *   <tr><td>{@code clear_period: true} 만</td><td>기간 제한 없음</td></tr>
 *   <tr><td>{@code clear_period: true, starts_on: A}</td><td>A 부터 계속</td></tr>
 *   <tr><td>{@code clear_period: true, ends_on: B}</td><td>즉시부터 B 까지</td></tr>
 *   <tr><td>{@code ends_on: B} 만</td><td>시작일은 유지, 종료일만 B</td></tr>
 *   <tr><td>아무것도 안 보냄</td><td>기간 그대로</td></tr>
 * </table>
 *
 * <p>이게 없으면 종료일을 잘못 넣은 공지를 되살릴 방법이 없습니다 — 날짜가 지나면
 * 목록·상세·팝업에서 전부 사라지는데, 사라진 이유가 오류가 아니라 설정이라 더 찾기 어렵습니다.
 *
 * @param isPopup 팝업 켜고 끄기. {@code null} 이면 그대로입니다 —
 *                {@code false} 를 보내야 꺼집니다
 */
public record UpdateAnnouncementRequest(
        AnnouncementType type,

        @Size(max = Announcement.TITLE_MAX_LENGTH, message = "제목은 500자를 넘을 수 없습니다.")
        String title,

        String content,

        Boolean isPopup,

        LocalDate startsOn,

        LocalDate endsOn,

        Boolean clearPeriod) {

    public boolean shouldClearPeriod() {
        return Boolean.TRUE.equals(clearPeriod);
    }

    /** 기간 관련 필드를 하나라도 보냈는지. 아니면 기간을 건드리지 않습니다. */
    public boolean touchesPeriod() {
        return shouldClearPeriod() || startsOn != null || endsOn != null;
    }
}
