package today.inform.inform.admin.article.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.request.ArticleIdsRequest;
import today.inform.inform.admin.article.dto.request.ChangeStatusRequest;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
import today.inform.inform.admin.article.dto.response.ReviewStats;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.article.service.AdminArticleService;
import today.inform.inform.admin.article.service.AdminArticleWriteService;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * 관리자 공지 검수. <b>{@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다</b>
 * ({@code SecurityConfig}). 그래서 메서드마다 권한을 다시 확인하지 않습니다 —
 * 확인을 각 메서드에 흩어 두면 새 엔드포인트에서 빠뜨리기 쉽고,
 * 빠뜨린 자리는 <b>미배포 공지를 아무나 볼 수 있는 구멍</b>이 됩니다.
 *
 * <p>사용자용 {@code ArticleController} 와 경로·응답이 겹치지 않게 완전히 분리했습니다.
 */
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final AdminArticleService adminArticleService;
    private final AdminArticleWriteService writeService;

    /** ADM-02 대시보드 카드 세 개. */
    @GetMapping("/stats")
    public ApiResponse<ReviewStats> stats() {
        return ApiResponse.success(adminArticleService.stats());
    }

    /**
     * ADM-03 / ADM-12 목록.
     *
     * @param status      <b>필수 조건</b>입니다. 안 보내면 검수 대기가 됩니다 —
     *                    "전체" 를 허용하면 관리자 첫 화면이 수만 건 스캔이 됩니다
     * @param needsCheck  ADM-12. 중복 의심이거나 정보가 빠진 공지만
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminArticleSummary>> search(
            @RequestParam(name = "status", required = false) ArticleStatus status,
            @RequestParam(name = "article_id", required = false) Long articleId,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "vendor_id", required = false) Long vendorId,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "starts_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsFrom,
            @RequestParam(name = "ends_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsTo,
            @RequestParam(name = "needs_check", required = false) Boolean needsCheck,
            @PageableDefault(size = 20) Pageable pageable) {

        AdminArticleSearchCondition condition = new AdminArticleSearchCondition(
                status, articleId, title, vendorId, categoryId, startsFrom, endsTo, needsCheck);

        return ApiResponse.success(PageResponse.from(adminArticleService.search(condition, pageable)));
    }

    /**
     * ADM-06 게시글 번호 중복 확인.
     *
     * <p><b>보장이 아니라 힌트입니다.</b> 두 관리자가 같은 번호를 확인하면 둘 다 통과하고
     * 저장에서 한 명이 409 를 받습니다. 클라이언트는 그때 재입력을 유도해야 합니다.
     */
    @GetMapping("/check-id")
    public ApiResponse<Map<String, Boolean>> checkId(@RequestParam(name = "article_id") Long articleId) {
        return ApiResponse.success(Map.of("available", writeService.isIdAvailable(articleId)));
    }

    /** ADM-06 / CLB-01 생성. 번호를 지정하면 시퀀스도 함께 밀어올립니다. */
    @PostMapping
    public ApiResponse<AdminArticleDetail> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SaveArticleRequest request) {
        return ApiResponse.success(writeService.create(request, principal.userId()));
    }

    /** ADM-04 상세. 검수 대기든 휴지통이든 열립니다. */
    @GetMapping("/{articleId}")
    public ApiResponse<AdminArticleDetail> detail(@PathVariable Long articleId) {
        return ApiResponse.success(writeService.getDetail(articleId));
    }

    /**
     * ADM-05 / CLB-02 수정.
     *
     * <p>상태는 여기서 바꾸지 않습니다 — 전이 규칙과 감사 이력을 타야 하므로
     * {@code PATCH /admin/articles/status} 로만 갑니다.
     */
    @PatchMapping("/{articleId}")
    public ApiResponse<AdminArticleDetail> update(@PathVariable Long articleId,
                                                  @Valid @RequestBody SaveArticleRequest request) {
        return ApiResponse.success(writeService.update(articleId, request));
    }

    /** ADM-09 휴지통 목록. "상태" 는 휴지통에 들어가기 직전 상태입니다. */
    @GetMapping("/trash")
    public ApiResponse<PageResponse<AdminArticleSummary>> trash(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(adminArticleService.listTrashed(pageable)));
    }

    /** ADM-11 상태 변경 이력. 최신순. */
    @GetMapping("/{articleId}/status-logs")
    public ApiResponse<List<StatusLogResponse>> statusLogs(@PathVariable Long articleId) {
        return ApiResponse.success(adminArticleService.statusLogs(articleId));
    }

    /**
     * ADM-07 상태 일괄 변경.
     *
     * <p>경로에 공지 번호가 없습니다. 여러 건을 한 번에 바꾸는 것이 기본 동작이라
     * {@code PATCH /admin/articles/{id}} 를 반복 호출하게 두면
     * 이력이 요청 수만큼 쪼개지고 사유도 건별로 달라집니다.
     */
    @PatchMapping("/status")
    public ApiResponse<Map<String, Integer>> changeStatus(@Valid @RequestBody ChangeStatusRequest request) {
        int changed = adminArticleService.changeStatus(
                request.articleIds(), request.status(), request.memo());
        return ApiResponse.success(Map.of("changed_count", changed));
    }

    /** ADM-08 휴지통 이동. */
    @PostMapping("/trash")
    public ApiResponse<Map<String, Integer>> moveToTrash(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(Map.of(
                "changed_count", adminArticleService.moveToTrash(request.articleIds(), request.memo())));
    }

    /** ADM-09 복구. 이력의 직전 상태로만 갑니다. */
    @PostMapping("/restore")
    public ApiResponse<Map<String, Integer>> restore(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(Map.of(
                "changed_count", adminArticleService.restore(request.articleIds(), request.memo())));
    }
}
