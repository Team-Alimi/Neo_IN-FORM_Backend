package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.CategoryListResponse;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.service.CommonService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommonController {

    private final CommonService commonService;

    @GetMapping("/vendors")
    public ApiResponse<List<VendorListResponse>> getVendors(
            @RequestParam(name = "type", required = false) VendorType type
    ) {
        return ApiResponse.success(commonService.getVendors(type));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryListResponse>> getCategories() {
        return ApiResponse.success(commonService.getCategories());
    }
}
