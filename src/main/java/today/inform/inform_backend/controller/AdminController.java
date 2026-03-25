package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.SandboxArticleListResponse;
import today.inform.inform_backend.dto.SandboxArticleResponse;
import today.inform.inform_backend.dto.SandboxCountsResponse;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.entity.SchoolArticleSandbox;
import today.inform.inform_backend.entity.SchoolArticleVendorSandbox;
import today.inform.inform_backend.entity.AttachmentSandbox;
import today.inform.inform_backend.dto.AdminUnifiedDetailResponse;
import today.inform.inform_backend.dto.AdminUnifiedUpdateRequest;
import today.inform.inform_backend.dto.AdminArticleCreateRequest;
import today.inform.inform_backend.service.SchoolArticleSandboxService;
import today.inform.inform_backend.service.SchoolArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SchoolArticleSandboxService sandboxService;
    private final SchoolArticleService schoolArticleService;

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<String>> checkAdmin() {
        return ResponseEntity.ok(ApiResponse.success("관리자 권한 확인 성공"));
    }

    /**
     * 샌드박스 상태별 통계 조회
     */
    @GetMapping("/sandbox/counts")
    public ResponseEntity<ApiResponse<SandboxCountsResponse>> getSandboxCounts() {
        Map<String, Long> counts = sandboxService.getSandboxCounts();
        SandboxCountsResponse response = SandboxCountsResponse.builder()
                .inspectedYet(counts.get("inspected_yet"))
                .reflectionWaiting(counts.get("reflection_waiting"))
                .suspectedDuplicate(counts.get("suspected_duplicate"))
                .garbage(counts.get("garbage"))
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 샌드박스 상태별 목록 조회 (페이징 지원)
     */
    @GetMapping("/sandbox/articles")
    public ResponseEntity<ApiResponse<SandboxArticleListResponse>> getSandboxArticles(
            @RequestParam("status") AdminStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        
        Page<SchoolArticleSandbox> articlePage = sandboxService.getArticlesByStatus(status, PageRequest.of(page - 1, size));
        
        List<SandboxArticleResponse> articleResponses = new ArrayList<>();
        for (SchoolArticleSandbox article : articlePage.getContent()) {
            List<SchoolArticleVendorSandbox> vendors = sandboxService.getVendors(article.getSandboxId());
            
            List<SandboxArticleResponse.VendorResponse> vendorResponses = vendors.stream()
                    .map(v -> SandboxArticleResponse.VendorResponse.builder()
                            .vendorId(v.getVendor().getVendorId())
                            .vendorName(v.getVendor().getVendorName())
                            .vendorInitial(v.getVendor().getVendorInitial())
                            .vendorType(v.getVendor().getVendorType().name())
                            .build())
                    .collect(Collectors.toList());

            SandboxArticleResponse.CategoryResponse categoryResponse = article.getCategory() != null ? 
                    SandboxArticleResponse.CategoryResponse.builder()
                            .categoryId(article.getCategory().getCategoryId())
                            .categoryName(article.getCategory().getCategoryName())
                            .build() : null;

            articleResponses.add(SandboxArticleResponse.builder()
                    .sandboxId(article.getSandboxId())
                    .title(article.getTitle())
                    .categories(categoryResponse)
                    .adminStatus(article.getAdminStatus().name())
                    .previousStatus(article.getPreviousStatus() != null ? article.getPreviousStatus().name() : null)
                    .startDate(article.getStartDate())
                    .dueDate(article.getDueDate())
                    .createdAt(article.getCreatedAt())
                    .updatedAt(article.getUpdatedAt())
                    .vendors(vendorResponses)
                    .build());
        }

        SandboxArticleListResponse.PageInfo pageInfo = SandboxArticleListResponse.PageInfo.builder()
                .currentPage(page)
                .totalPages(articlePage.getTotalPages())
                .totalArticles(articlePage.getTotalElements())
                .hasNext(articlePage.hasNext())
                .build();

        SandboxArticleListResponse response = SandboxArticleListResponse.builder()
                .pageInfo(pageInfo)
                .sandboxArticles(articleResponses)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * [통합] 관리자 게시글 상세 조회 (sandbox/service 통합)
     */
    @GetMapping("/articles/{source}/{id}")
    public ResponseEntity<ApiResponse<AdminUnifiedDetailResponse>> getUnifiedArticleDetail(
            @PathVariable("source") String source,
            @PathVariable("id") Integer id) {
        AdminUnifiedDetailResponse response;
        if ("sandbox".equals(source)) {
            response = sandboxService.getAdminSandboxDetail(id);
        } else if ("service".equals(source)) {
            response = schoolArticleService.getAdminArticleDetail(id);
        } else {
            throw new today.inform.inform_backend.common.exception.BusinessException(
                    today.inform.inform_backend.common.exception.ErrorCode.INVALID_INPUT_VALUE);
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * [통합] 관리자 게시글 수정 (sandbox/service 통합)
     */
    @PatchMapping("/articles/{source}/{id}")
    public ResponseEntity<ApiResponse<Void>> updateUnifiedArticle(
            @PathVariable("source") String source,
            @PathVariable("id") Integer id,
            @RequestBody AdminUnifiedUpdateRequest request) {
        if ("sandbox".equals(source)) {
            sandboxService.updateArticle(id, request);
        } else if ("service".equals(source)) {
            schoolArticleService.updateArticleDirectly(id, request);
        } else {
            throw new today.inform.inform_backend.common.exception.BusinessException(
                    today.inform.inform_backend.common.exception.ErrorCode.INVALID_INPUT_VALUE);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 샌드박스 게시글 상태 변경
     */
    @PatchMapping("/sandbox/articles/status")
    public ResponseEntity<ApiResponse<Void>> updateSandboxStatuses(
            @RequestParam("ids") List<Integer> ids,
            @RequestParam("status") AdminStatus status) {
        sandboxService.updateStatuses(ids, status);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * [핵심] 샌드박스 게시글 실서비스 반영 (Deploy) - 단건/다중 지원
     */
    @PostMapping("/sandbox/articles/deploy")
    public ResponseEntity<ApiResponse<List<Integer>>> deploySandboxArticles(
            @RequestParam("ids") List<Integer> ids) {
        List<Integer> newArticleIds = sandboxService.deployArticles(ids);
        return ResponseEntity.ok(ApiResponse.success(newArticleIds));
    }

    /**
     * 샌드박스 게시글 삭제 - 단건/다중 지원
     */
    @DeleteMapping("/sandbox/articles/delete")
    public ResponseEntity<ApiResponse<Void>> deleteSandboxArticles(
            @RequestParam("ids") List<Integer> ids) {
        sandboxService.deleteArticles(ids);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 휴지통 게시글 복구 - 단건/다중 지원
     */
    @PatchMapping("/sandbox/articles/restore")
    public ResponseEntity<ApiResponse<Void>> restoreSandboxArticles(
            @RequestParam("ids") List<Integer> ids) {
        sandboxService.restoreArticles(ids);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 관리자: 서비스 DB 직접 등록
     */
    @PostMapping("/articles")
    public ResponseEntity<ApiResponse<Integer>> createArticleDirectly(
            @RequestBody AdminArticleCreateRequest request) {
        Integer articleId = schoolArticleService.createArticleDirectly(request);
        return ResponseEntity.ok(ApiResponse.success(articleId));
    }

    /**
     * 관리자: 서비스 DB 게시글 중복 ID 확인
     */
    @GetMapping("/articles/check-id")
    public ResponseEntity<ApiResponse<Boolean>> checkArticleIdExists(
            @RequestParam("article_id") Integer articleId) {
        boolean exists = schoolArticleService.checkArticleIdExists(articleId);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
