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
import today.inform.inform_backend.dto.SandboxArticleUpdateRequest;
import today.inform.inform_backend.service.SchoolArticleSandboxService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SchoolArticleSandboxService sandboxService;

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
     * 샌드박스 상태별 목록 조회
     */
    @GetMapping("/sandbox/articles")
    public ResponseEntity<ApiResponse<SandboxArticleListResponse>> getSandboxArticles(
            @RequestParam("status") AdminStatus status) {
        
        List<SchoolArticleSandbox> articles = sandboxService.getArticlesByStatus(status);
        
        List<SandboxArticleResponse> articleResponses = articles.stream()
                .map(article -> SandboxArticleResponse.builder()
                        .sandboxId(article.getSandboxId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .categoryName(article.getCategory() != null ? article.getCategory().getCategoryName() : null)
                        .adminStatus(article.getAdminStatus().name())
                        .startDate(article.getStartDate())
                        .dueDate(article.getDueDate())
                        .createdAt(article.getCreatedAt())
                        .vendorNames(sandboxService.getVendors(article.getSandboxId()).stream()
                                .map(v -> v.getVendor().getVendorName())
                                .collect(Collectors.toList()))
                        .attachmentUrls(sandboxService.getAttachments(article.getSandboxId()).stream()
                                .map(a -> a.getAttachmentUrl())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(SandboxArticleListResponse.builder()
                .articles(articleResponses)
                .build()));
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
}
