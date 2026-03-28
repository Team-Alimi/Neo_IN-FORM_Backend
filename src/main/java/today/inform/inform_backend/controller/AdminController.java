package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.SandboxCountsResponse;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.dto.AdminUnifiedDetailResponse;
import today.inform.inform_backend.dto.AdminUnifiedUpdateRequest;
import today.inform.inform_backend.dto.AdminArticleCreateRequest;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.dto.SchoolArticleResponse;
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

    private final SchoolArticleService schoolArticleService;

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<String>> checkAdmin() {
        return ResponseEntity.ok(ApiResponse.success("관리자 권한 확인 성공"));
    }

    /**
     * 미배포 게시글 상태별 통계 조회
     */
    @GetMapping("/articles/counts")
    public ResponseEntity<ApiResponse<SandboxCountsResponse>> getUnpublishedCounts() {
        Map<String, Long> counts = schoolArticleService.getUnpublishedCounts();
        SandboxCountsResponse response = SandboxCountsResponse.builder()
                .inspectedYet(counts.get("inspected_yet"))
                .reflectionWaiting(counts.get("reflection_waiting"))
                .suspectedDuplicate(counts.get("suspected_duplicate"))
                .garbage(counts.get("garbage"))
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 미배포 게시글 상태별 목록 조회 (페이징)
     */
    @GetMapping("/articles")
    public ResponseEntity<ApiResponse<SchoolArticleListResponse>> getUnpublishedArticles(
            @RequestParam("status") AdminStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Page<SchoolArticle> articlePage = schoolArticleService.getUnpublishedArticlesByStatus(status, PageRequest.of(page - 1, size));

        List<SchoolArticleResponse> articleResponses = new ArrayList<>();
        for (SchoolArticle article : articlePage.getContent()) {
            List<SchoolArticleVendor> vendors = schoolArticleService.getVendorsByArticle(article);

            List<SchoolArticleResponse.VendorResponse> vendorResponses = vendors.stream()
                    .map(v -> SchoolArticleResponse.VendorResponse.builder()
                            .vendorId(v.getVendor().getVendorId())
                            .vendorName(v.getVendor().getVendorName())
                            .vendorInitial(v.getVendor().getVendorInitial())
                            .vendorType(v.getVendor().getVendorType().name())
                            .build())
                    .collect(Collectors.toList());

            SchoolArticleResponse.CategoryResponse categoryResponse = article.getCategory() != null ?
                    SchoolArticleResponse.CategoryResponse.builder()
                            .categoryId(article.getCategory().getCategoryId())
                            .categoryName(article.getCategory().getCategoryName())
                            .build() : null;

            articleResponses.add(SchoolArticleResponse.builder()
                    .articleId(article.getArticleId())
                    .title(article.getTitle())
                    .categories(categoryResponse)
                    .adminStatus(article.getAdminStatus().name())
                    .previousStatus(article.getPreviousStatus() != null ? article.getPreviousStatus().name() : null)
                    .startDate(article.getStartDate())
                    .dueDate(article.getDueDate())
                    .createdAt(article.getCreatedAt())
                    .updatedAt(article.getUpdatedAt())
                    .lastModifiedAdminName(article.getLastModifiedAdmin() != null ? article.getLastModifiedAdmin().getName() : null)
                    .adminModifiedAt(article.getAdminModifiedAt())
                    .vendors(vendorResponses)
                    .build());
        }

        SchoolArticleListResponse.PageInfo pageInfo = SchoolArticleListResponse.PageInfo.builder()
                .currentPage(page)
                .totalPages(articlePage.getTotalPages())
                .totalArticles(articlePage.getTotalElements())
                .hasNext(articlePage.hasNext())
                .build();

        SchoolArticleListResponse response = SchoolArticleListResponse.builder()
                .pageInfo(pageInfo)
                .schoolArticles(articleResponses)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 관리자 게시글 상세 조회
     */
    @GetMapping("/articles/{id}")
    public ResponseEntity<ApiResponse<AdminUnifiedDetailResponse>> getArticleDetail(
            @PathVariable("id") Integer id) {
        AdminUnifiedDetailResponse response = schoolArticleService.getAdminArticleDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 관리자 게시글 수정 (배포/미배포 공통)
     */
    @PatchMapping("/articles/{id}")
    public ResponseEntity<ApiResponse<Void>> updateArticle(
            @PathVariable("id") Integer id,
            @RequestBody AdminUnifiedUpdateRequest request,
            @AuthenticationPrincipal Integer adminUserId) {
        schoolArticleService.updateArticle(id, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 게시글 상태 변경 (일괄)
     */
    @PatchMapping("/articles/status")
    public ResponseEntity<ApiResponse<Void>> updateStatuses(
            @RequestParam("ids") List<Integer> ids,
            @RequestParam("status") AdminStatus status,
            @AuthenticationPrincipal Integer adminUserId) {
        schoolArticleService.updateStatuses(ids, status, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 게시글 서비스 배포 (Deploy)
     */
    @PostMapping("/articles/deploy")
    public ResponseEntity<ApiResponse<List<Integer>>> deployArticles(
            @RequestParam("ids") List<Integer> ids,
            @AuthenticationPrincipal Integer adminUserId) {
        List<Integer> deployedIds = schoolArticleService.deployArticles(ids, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(deployedIds));
    }

    /**
     * 게시글 영구 삭제
     */
    @DeleteMapping("/articles/delete")
    public ResponseEntity<ApiResponse<Void>> deleteArticles(
            @RequestParam("ids") List<Integer> ids) {
        schoolArticleService.deleteArticles(ids);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 휴지통 게시글 복구
     */
    @PatchMapping("/articles/restore")
    public ResponseEntity<ApiResponse<Void>> restoreArticles(
            @RequestParam("ids") List<Integer> ids,
            @AuthenticationPrincipal Integer adminUserId) {
        schoolArticleService.restoreArticles(ids, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 관리자: 서비스 DB 직접 등록
     */
    @PostMapping("/articles/create")
    public ResponseEntity<ApiResponse<Integer>> createArticleDirectly(
            @RequestBody AdminArticleCreateRequest request,
            @AuthenticationPrincipal Integer adminUserId) {
        Integer articleId = schoolArticleService.createArticleDirectly(request, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(articleId));
    }

    /**
     * 관리자: 게시글 ID 중복 확인
     */
    @GetMapping("/articles/check-id")
    public ResponseEntity<ApiResponse<Boolean>> checkArticleIdExists(
            @RequestParam("article_id") Integer articleId) {
        boolean exists = schoolArticleService.checkArticleIdExists(articleId);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
