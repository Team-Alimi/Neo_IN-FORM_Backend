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
import today.inform.inform_backend.dto.SandboxArticleUpdateRequest;
import today.inform.inform_backend.dto.SandboxArticleDetailResponse;
import today.inform.inform_backend.dto.AdminArticleCreateRequest;
import today.inform.inform_backend.dto.AdminArticleUpdateRequest;
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
     * 샌드박스 게시글 상세 조회
     */
    @GetMapping("/sandbox/articles/{id}")
    public ResponseEntity<ApiResponse<SandboxArticleDetailResponse>> getSandboxArticle(@PathVariable("id") Integer id) {
        SchoolArticleSandbox article = sandboxService.getArticleDetail(id);
        
        SandboxArticleDetailResponse.CategoryResponse categoryResponse = article.getCategory() != null ? 
                SandboxArticleDetailResponse.CategoryResponse.builder()
                        .categoryId(article.getCategory().getCategoryId())
                        .categoryName(article.getCategory().getCategoryName())
                        .build() : null;
        
        SandboxArticleDetailResponse response = SandboxArticleDetailResponse.builder()
                .sandboxId(article.getSandboxId())
                .title(article.getTitle())
                .content(article.getContent())
                .categories(categoryResponse)
                .adminStatus(article.getAdminStatus().name())
                .startDate(article.getStartDate())
                .dueDate(article.getDueDate())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .vendors(sandboxService.getVendors(id).stream()
                        .map(v -> SandboxArticleDetailResponse.VendorResponse.builder()
                                .vendorId(v.getVendor().getVendorId())
                                .vendorName(v.getVendor().getVendorName())
                                .vendorInitial(v.getVendor().getVendorInitial())
                                .vendorType(v.getVendor().getVendorType().name())
                                .originalUrl(v.getOriginalUrl())
                                .build())
                        .collect(Collectors.toList()))
                .attachments(sandboxService.getAttachments(id).stream()
                        .map(a -> SandboxArticleDetailResponse.AttachmentResponse.builder()
                                .fileId(a.getId())
                                .attachmentUrl(a.getAttachmentUrl())
                                .build())
                        .collect(Collectors.toList()))
                .build();
                
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 샌드박스 게시글 상세 정보 수정
     */
    @PatchMapping("/sandbox/articles/{id}")
    public ResponseEntity<ApiResponse<Void>> updateSandboxArticle(
            @PathVariable("id") Integer id,
            @RequestBody SandboxArticleUpdateRequest request) {
        sandboxService.updateArticle(id, request);
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
    @DeleteMapping("/sandbox/articles")
    public ResponseEntity<ApiResponse<Void>> deleteSandboxArticles(
            @RequestParam("ids") List<Integer> ids) {
        sandboxService.deleteArticles(ids);
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

    /**
     * 관리자: 서비스 DB 직접 수정
     */
    @PatchMapping("/articles/{id}")
    public ResponseEntity<ApiResponse<Void>> updateArticleDirectly(
            @PathVariable("id") Integer id,
            @RequestBody AdminArticleUpdateRequest request) {
        schoolArticleService.updateArticleDirectly(id, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
