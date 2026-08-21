package today.inform.inform.vendor.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.vendor.dto.response.VendorResponse;
import today.inform.inform.vendor.service.VendorQueryService;

/**
 * COM-01 제공처 목록. <b>비로그인도 열립니다</b> — 온보딩 첫 화면이 이 목록으로 그려집니다.
 *
 * <p>관리자용 목록({@code /admin/vendors})과 <b>물리적으로 분리</b>합니다.
 * 한 엔드포인트에 "관리자면 비활성도" 분기를 두면, 그 분기를 잘못 건드리는 순간
 * 접어 둔 제공처가 온보딩 선택지에 다시 나타납니다. 오류가 아니라
 * <b>사용자가 없어진 학과를 구독하게 되는</b> 조용한 사고입니다.
 */
@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorQueryService vendorQueryService;

    /**
     * 활성 제공처만, 이름 오름차순.
     *
     * @param type SCHOOL(학과·기관) 또는 CLUB(동아리). 생략하면 전부
     */
    @GetMapping
    public ApiResponse<List<VendorResponse>> list(
            @RequestParam(name = "type", required = false) SourceType type) {
        return ApiResponse.success(vendorQueryService.findActive(type));
    }
}
