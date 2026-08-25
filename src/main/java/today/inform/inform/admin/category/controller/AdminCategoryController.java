package today.inform.inform.admin.category.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.category.dto.request.CreateCategoryRequest;
import today.inform.inform.admin.category.dto.request.UpdateCategoryRequest;
import today.inform.inform.admin.category.dto.response.AdminCategoryResponse;
import today.inform.inform.admin.category.service.AdminCategoryService;
import today.inform.inform.global.response.ApiResponse;

/** CAT-01 ~ CAT-03 카테고리 관리. {@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다. */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService categoryService;

    /**
     * 관리 화면 목록. 비활성 분류도 포함하고, 각 항목에 {@code in_use} 가 붙습니다.
     *
     * <p>사용자용 목록(COM-02)과 별개입니다 — 그쪽은 활성만 내보냅니다.
     */
    @GetMapping
    public ApiResponse<List<AdminCategoryResponse>> search(
            @RequestParam(name = "is_active", required = false) Boolean active) {
        return ApiResponse.success(categoryService.search(active));
    }

    /** CAT-01. {@code code} 는 크롤러 AI 분류 목록과 <b>미리 맞춰 두고</b> 넣어야 합니다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success(categoryService.create(request));
    }

    /** CAT-02. 이름·정렬·활성 여부만 바뀝니다. */
    @PatchMapping("/{categoryId}")
    public ApiResponse<AdminCategoryResponse> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success(categoryService.update(categoryId, request));
    }

    /** CAT-03. 쓰이고 있으면 409 입니다. 그 경우 화면은 비활성화를 안내해야 합니다. */
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> delete(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return ApiResponse.success(null);
    }
}
