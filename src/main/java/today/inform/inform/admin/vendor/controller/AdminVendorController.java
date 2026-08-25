package today.inform.inform.admin.vendor.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.vendor.dto.request.CreateVendorRequest;
import today.inform.inform.admin.vendor.dto.request.UpdateVendorRequest;
import today.inform.inform.admin.vendor.dto.response.AdminVendorResponse;
import today.inform.inform.admin.vendor.service.AdminVendorService;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.response.ApiResponse;

/**
 * VND-01 ~ VND-03 제공처 관리.
 *
 * <p>{@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다({@code SecurityConfig}).
 *
 * <p><b>DELETE 는 없습니다.</b> {@code article_vendors} 가 {@code ON DELETE RESTRICT} 라
 * 공지가 하나라도 붙어 있으면 DB 가 거부하고, 안 붙어 있더라도 지우면 나중에 같은
 * {@code initial} 로 다시 만들었을 때 과거 공지와의 연결을 되살릴 수 없습니다. 비활성화로 갈음합니다.
 */
@RestController
@RequestMapping("/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final AdminVendorService vendorService;

    /**
     * 관리 화면 목록.
     *
     * <p>명세에는 없지만 필요합니다 — 수정·비활성화가 전부 {@code vendorId} 를 받는데,
     * 등록 응답을 놓치면 그 번호를 알 방법이 없습니다.
     * 사용자용 목록(COM-01)은 활성만 내보내므로 관리 화면이 쓸 수 없습니다.
     */
    @GetMapping
    public ApiResponse<List<AdminVendorResponse>> search(
            @RequestParam(name = "type", required = false) SourceType type,
            @RequestParam(name = "is_active", required = false) Boolean active) {
        return ApiResponse.success(vendorService.search(type, active));
    }

    /** VND-01. 응답의 {@code warning} 에 크롤러 시드 등록 안내가 함께 나갑니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminVendorResponse> create(@Valid @RequestBody CreateVendorRequest request) {
        return ApiResponse.success(vendorService.create(request));
    }

    /** VND-02 수정 · VND-03 비활성화. 부분 수정이며 {@code null} 은 "그대로" 입니다. */
    @PatchMapping("/{vendorId}")
    public ApiResponse<AdminVendorResponse> update(
            @PathVariable Long vendorId,
            @Valid @RequestBody UpdateVendorRequest request) {
        return ApiResponse.success(vendorService.update(vendorId, request));
    }
}
