package today.inform.inform.admin.announcement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.announcement.dto.request.CreateAnnouncementRequest;
import today.inform.inform.admin.announcement.dto.request.UpdateAnnouncementRequest;
import today.inform.inform.admin.announcement.dto.response.AdminAnnouncementResponse;
import today.inform.inform.admin.announcement.service.AdminAnnouncementService;
import today.inform.inform.announcement.entity.AnnouncementStatus;
import today.inform.inform.announcement.entity.AnnouncementType;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * 서비스 공지 관리. <b>{@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다</b>
 * ({@code SecurityConfig}). 그래서 메서드마다 권한을 다시 확인하지 않습니다.
 *
 * <p>사용자용 {@code AnnouncementController} 와 경로·응답이 완전히 분리돼 있습니다 —
 * 이쪽만 임시저장·보관 공지를 봅니다.
 *
 * <p><b>DELETE 는 없습니다.</b> 공지는 "무엇을 언제 알렸는지" 의 기록이라 보관으로 갈음합니다.
 *
 * <p><b>응답의 {@code warnings} 를 화면에 띄워 주세요.</b> 저장은 성공했지만 사용자에게는
 * 아무것도 안 보이는 조합이 여럿 있습니다(팝업인데 임시저장, 종료일이 지난 발행 등).
 * 오류가 아니라서 상태 코드로는 알릴 수 없습니다.
 */
@RestController
@RequestMapping("/admin/announcements")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AdminAnnouncementService announcementService;

    /**
     * 관리 화면 목록. <b>임시저장·보관까지 전부</b> 나옵니다. 최근 작성순 고정입니다.
     *
     * @param status  상태 필터. 생략하면 전체
     * @param type    유형 필터. 생략하면 전체
     * @param isPopup 팝업 지정된 것만 / 아닌 것만. 생략하면 전체.
     *                <b>"지금 뜨는 팝업" 과 다릅니다</b> — 이건 플래그만 보고,
     *                실제로 뜨는 것은 상태와 기간까지 맞아야 합니다
     *                ({@code GET /announcements/popup} 이 그 결과입니다)
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminAnnouncementResponse>> search(
            @RequestParam(name = "status", required = false) AnnouncementStatus status,
            @RequestParam(name = "type", required = false) AnnouncementType type,
            @RequestParam(name = "is_popup", required = false) Boolean isPopup,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(
                announcementService.search(status, type, isPopup, pageable)));
    }

    /**
     * 등록. {@code status} 를 {@code PUBLISHED} 로 보내면 바로 발행됩니다.
     *
     * <p>작성자는 토큰에서 가져옵니다 — 요청 본문으로 받으면 다른 관리자 이름으로 적을 수 있습니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminAnnouncementResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        return ApiResponse.success(announcementService.create(request, principal.userId()));
    }

    /**
     * 수정. 부분 수정이며 {@code null} 은 "그대로" 입니다.
     *
     * <p><b>상태는 여기서 바뀌지 않습니다.</b> 발행·보관은 아래 두 경로로만 갑니다.
     *
     * <p>기간을 비우려면 {@code clear_period: true} 를 보내세요
     * ({@code UpdateAnnouncementRequest} 의 조합 표).
     */
    @PatchMapping("/{announcementId}")
    public ApiResponse<AdminAnnouncementResponse> update(
            @PathVariable Long announcementId,
            @Valid @RequestBody UpdateAnnouncementRequest request) {
        return ApiResponse.success(announcementService.update(announcementId, request));
    }

    /**
     * 발행. 임시저장·보관 → 노출 중.
     *
     * <p>이미 발행 중이면 400({@code INVALID_STATE_TRANSITION})입니다 — 멱등하게 통과시키면
     * 버튼을 눌렀을 때 "방금 발행됐다" 와 "이미 발행돼 있었다" 를 화면이 구분할 수 없습니다.
     *
     * <p>최초 발행 시각은 유지됩니다. 보관했다 다시 올려도 목록 순서가 바뀌지 않습니다.
     */
    @PostMapping("/{announcementId}/publish")
    public ApiResponse<AdminAnnouncementResponse> publish(@PathVariable Long announcementId) {
        return ApiResponse.success(announcementService.publish(announcementId));
    }

    /**
     * 보관. 목록·팝업에서 빠지고 기록은 남습니다.
     *
     * <p>팝업 설정은 그대로 둡니다 — 되살릴 때 원래대로 돌아옵니다.
     * 그래서 응답에 "팝업인데 안 뜬다" 경고가 함께 나갈 수 있습니다. 정상입니다.
     */
    @PostMapping("/{announcementId}/archive")
    public ApiResponse<AdminAnnouncementResponse> archive(@PathVariable Long announcementId) {
        return ApiResponse.success(announcementService.archive(announcementId));
    }
}
