package today.inform.inform.category.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.category.dto.response.CategoryResponse;
import today.inform.inform.category.service.CategoryQueryService;
import today.inform.inform.global.response.ApiResponse;

/**
 * COM-02 분류 목록. <b>비로그인도 열립니다</b> — 온보딩과 목록 필터가 이걸로 그려집니다.
 *
 * <p>관리자용({@code /admin/categories})과 분리한 이유는 {@code VendorController} 와 같습니다.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.success(categoryQueryService.findActive());
    }
}
