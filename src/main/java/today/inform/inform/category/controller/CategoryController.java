package today.inform.inform.category.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.category.dto.response.CategoryResponse;
import today.inform.inform.category.service.CategoryQueryService;
import today.inform.inform.global.response.ApiResponse;

/**
 * COM-02 분류 목록. <b>비로그인도 열립니다</b> — 온보딩과 목록 필터가 이걸로 그려집니다.
 *
 * <p>두 화면이 필요한 목록이 다릅니다. 온보딩은 고를 수 있는 것만, 목록 필터는 기타까지.
 * {@code include_unselectable} 로 가르며 <b>기본은 온보딩용</b>입니다.
 *
 * <p>관리자용({@code /admin/categories})과 분리한 이유는 {@code VendorController} 와 같습니다.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    /**
     * @param includeUnselectable 목록 필터용으로 <b>기타</b>까지 받으려면 {@code true}.
     *                            온보딩은 이 값을 주지 않습니다 — 기본이 곧 온보딩용입니다.
     */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(
            @RequestParam(name = "include_unselectable", defaultValue = "false")
            boolean includeUnselectable) {
        return ApiResponse.success(categoryQueryService.findActive(includeUnselectable));
    }
}
