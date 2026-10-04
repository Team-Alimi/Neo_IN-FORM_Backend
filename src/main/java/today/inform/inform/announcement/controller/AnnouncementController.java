package today.inform.inform.announcement.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.announcement.dto.response.AnnouncementDetail;
import today.inform.inform.announcement.dto.response.AnnouncementSummary;
import today.inform.inform.announcement.service.AnnouncementQueryService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;

/**
 * 서비스 공지 조회. <b>비로그인으로 열려 있습니다</b>
 * ({@code SecurityConfig} 가 {@code /announcements} 와 {@code /announcements/*} 를 허용).
 *
 * <p><b>경로 순서에 주의하세요.</b> {@code /popup} 이 {@code /{announcementId}} 보다
 * 앞에 선언돼 있습니다. Spring 은 더 구체적인 패턴을 먼저 고르므로 지금 동작은
 * 순서와 무관하지만, {@code announcementId} 가 {@code String} 으로 바뀌면 그때부터
 * {@code /popup} 이 공지 번호로 해석됩니다. 숫자 타입을 유지하세요.
 */
@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementQueryService announcementService;

    /**
     * 공지 목록. 최신 발행순 고정입니다.
     *
     * <p>{@code sort} 는 받지 않습니다 — 보내도 무시됩니다(서비스에서 버립니다).
     */
    @GetMapping
    public ApiResponse<PageResponse<AnnouncementSummary>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(announcementService.list(pageable)));
    }

    /**
     * 지금 띄울 팝업 전체.
     *
     * <p><b>앱 진입 때 한 번 부르면 됩니다.</b> 여러 건이 올 수 있고, 없으면 빈 배열입니다.
     *
     * <p>"다시 보지 않기" · "7일간 보지 않기" 는 <b>클라이언트가 처리합니다.</b>
     * 서버는 누가 무엇을 닫았는지 저장하지 않습니다 — 기기별 설정이고, 기억하려면
     * 공지 하나당 사용자 수만큼 행이 쌓입니다. 응답의 {@code id} 를
     * {@code localStorage} 에 "언제까지 숨김" 과 함께 적어 두고 걸러 주세요.
     */
    @GetMapping("/popup")
    public ApiResponse<List<AnnouncementDetail>> popups() {
        return ApiResponse.success(announcementService.popups());
    }

    /**
     * 공지 상세.
     *
     * <p>발행 중이고 노출 기간 안인 것만 열립니다. 그 밖은 전부 404
     * ({@code ANNOUNCEMENT_NOT_FOUND})입니다 — 임시저장이든 기간이 지났든 구분하지 않습니다.
     */
    @GetMapping("/{announcementId}")
    public ApiResponse<AnnouncementDetail> detail(@PathVariable Long announcementId) {
        return ApiResponse.success(announcementService.detail(announcementId));
    }
}
