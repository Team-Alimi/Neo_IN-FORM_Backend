package today.inform.inform.admin.article.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.request.ArticleIdsRequest;
import today.inform.inform.admin.article.dto.request.ChangeStatusRequest;
import today.inform.inform.admin.article.dto.request.MergeArticlesRequest;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.article.dto.response.DuplicateCandidate;
import today.inform.inform.admin.article.dto.response.MergeResult;
import today.inform.inform.admin.article.dto.response.ReviewStats;
import today.inform.inform.admin.article.dto.response.SimilarComparison;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.article.service.AdminArticleService;
import today.inform.inform.admin.article.service.AdminArticleWriteService;
import today.inform.inform.admin.article.service.ArticleMergeService;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
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
 *
 * <p><b>벌크 작업은 전부 {@code POST /bulk/*} 입니다</b>(명세 4.8 공통 규약).
 * 요청 키는 {@code ids} 이고, 응답은 {@code succeeded} / {@code failed} 로
 * <b>부분 성공</b>을 돌려줍니다 — 한 건이 실패해도 나머지는 처리됩니다.
 */
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    /** 중복 확인 결과 상한. 화면은 "이미 있는지" 만 판단하면 되므로 몇 건이면 충분합니다. */
    private static final int DUPLICATE_LIMIT = 20;

    private final AdminArticleService adminArticleService;
    private final AdminArticleWriteService writeService;
    private final ArticleMergeService mergeService;

    /** ADM-02 대시보드. 상태별 건수 + 중복 의심·재검수 대기 두 축. */
    @GetMapping("/stats")
    public ApiResponse<ReviewStats> stats() {
        return ApiResponse.success(adminArticleService.stats());
    }

    /**
     * ADM-03 목록 (CLB-04 임시저장 목록 겸용).
     *
     * @param statuses    복수 지정 가능. <b>생략하면 휴지통을 뺀 전체</b>입니다.
     *                    {@code status=DRAFT} 로 부르면 그대로 임시저장 목록이 됩니다
     * @param needsReview 원본 수정으로 재검수 대기인 것만. 상태와 독립된 축입니다
     * @param needsCheck  ADM-12 "확인 필요" — 중복 의심이거나 정보가 빠진 것
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminArticleSummary>> search(
            @RequestParam(name = "status", required = false) List<ArticleStatus> statuses,
            @RequestParam(name = "source_type", required = false) SourceType sourceType,
            @RequestParam(name = "article_id", required = false) Long articleId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "vendor_id", required = false) Long vendorId,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "starts_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsFrom,
            @RequestParam(name = "ends_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsTo,
            @RequestParam(name = "needs_review", required = false) Boolean needsReview,
            @RequestParam(name = "needs_check", required = false) Boolean needsCheck,
            @PageableDefault(size = 20) Pageable pageable) {

        AdminArticleSearchCondition condition = new AdminArticleSearchCondition(
                statuses, sourceType, articleId, keyword, vendorId, categoryId,
                startsFrom, endsTo, needsReview, needsCheck);

        return ApiResponse.success(PageResponse.from(adminArticleService.search(condition, pageable)));
    }

    /**
     * ADM-12 중복 확인.
     *
     * <p>수기 등록 전에 <b>같은 원본이 이미 들어와 있는지</b> 봅니다.
     * {@code external_key} 는 원본 게시판 글 번호라 정확 일치, {@code title} 은 부분 일치입니다.
     * 둘 다 비우면 빈 결과입니다 — 조건 없이 전체를 훑을 이유가 없습니다.
     */
    @GetMapping("/duplicates")
    public ApiResponse<DuplicateCandidate.Result> duplicates(
            @RequestParam(name = "external_key", required = false) String externalKey,
            @RequestParam(name = "title", required = false) String title) {
        return ApiResponse.success(
                adminArticleService.findDuplicates(externalKey, title, DUPLICATE_LIMIT));
    }

    /**
     * ADM-06 / CLB-01 생성. 번호를 지정하면 시퀀스도 함께 밀어올립니다.
     *
     * <p>응답은 <b>{@code {"id": ...}} 뿐</b>입니다(명세). 상세가 필요하면
     * {@code GET /admin/articles/{id}} 를 부릅니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SaveArticleRequest request) {
        return ApiResponse.success(Map.of("id", writeService.create(request, principal.userId())));
    }

    /** ADM-04 상세. 검수 대기든 휴지통이든 열립니다. */
    @GetMapping("/{articleId}")
    public ApiResponse<AdminArticleDetail> detail(@PathVariable Long articleId) {
        return ApiResponse.success(writeService.getDetail(articleId));
    }

    /**
     * ADM-05 / CLB-02 수정. <b>부분 수정입니다</b>(명세).
     *
     * <p>보낸 필드만 반영합니다. {@code vendors}·{@code category_ids}·{@code attachments} 는
     * <b>보내면 전체 교체, 안 보내면 유지</b>입니다 — 생략이 곧 유지라서
     * 제목 하나 고치려고 저장한 요청이 분류를 통째로 지우는 일이 없습니다.
     */
    @PatchMapping("/{articleId}")
    public ApiResponse<AdminArticleDetail> update(
            @PathVariable Long articleId,
            @Valid @RequestBody SaveArticleRequest request) {
        return ApiResponse.success(writeService.update(articleId, request));
    }

    /** ADM-09 휴지통 목록. 각 항목에 "휴지통 직전 상태" 가 함께 나갑니다. */
    @GetMapping("/trash")
    public ApiResponse<PageResponse<AdminArticleSummary>> trash(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(adminArticleService.listTrashed(pageable)));
    }

    /** ADM-11 상태 변경 이력. {@code changed_by} 가 null 이면 시스템(수집기·스케줄러)입니다. */
    @GetMapping("/{articleId}/status-logs")
    public ApiResponse<List<StatusLogResponse>> statusLogs(@PathVariable Long articleId) {
        return ApiResponse.success(adminArticleService.statusLogs(articleId));
    }

    /** ADM-12 유사 공지 비교. 병합 판단을 하려면 두 글을 나란히 봐야 합니다. */
    @GetMapping("/{articleId}/similar")
    public ApiResponse<SimilarComparison> similar(@PathVariable Long articleId) {
        return ApiResponse.success(mergeService.compareWithSimilar(articleId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 벌크 작업 — 전부 부분 성공. 실패한 건만 사유와 함께 돌아옵니다.
    // ─────────────────────────────────────────────────────────────────────────

    /** ADM-06 상태 일괄 변경. */
    @PostMapping("/bulk/status")
    public ApiResponse<BulkResult> bulkStatus(@Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.success(
                adminArticleService.changeStatus(request.ids(), request.status(), request.memo()));
    }

    /** ADM-07 / CLB-03 배포. */
    @PostMapping("/bulk/publish")
    public ApiResponse<BulkResult> bulkPublish(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(adminArticleService.publish(request.ids(), request.memo()));
    }

    /** ADM-08 휴지통 이동. 현재 상태가 이력에 남아야 나중에 복구할 곳을 알 수 있습니다. */
    @PostMapping("/bulk/trash")
    public ApiResponse<BulkResult> bulkTrash(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(adminArticleService.moveToTrash(request.ids(), request.memo()));
    }

    /** ADM-09 복구. 이력의 <b>직전 상태</b>로만 갑니다. 휴지통이 아니면 {@code NOT_IN_TRASH}. */
    @PostMapping("/bulk/restore")
    public ApiResponse<BulkResult> bulkRestore(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(adminArticleService.restore(request.ids(), request.memo()));
    }

    /**
     * ADM-10 영구 삭제.
     *
     * <p><b>휴지통에 있는 공지만</b> 지울 수 있습니다. 되돌릴 수 없는 조작이라
     * 한 단계를 거치게 해 두었습니다. 첨부·북마크·알림·이력이 CASCADE 로 지워지고
     * <b>S3 객체도 함께 삭제</b>됩니다.
     */
    @PostMapping("/bulk/delete")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(mergeService.deletePermanently(request.ids()));
    }

    /**
     * ADM-03a 재검수 완료.
     *
     * <p>크롤러가 원본 수정을 감지해 <b>검수 대기로 내려간</b> 공지를 다시 배포합니다.
     * 그 공지는 지금 피드에서 사라져 있으므로 검수 통과와 발행을 한 번에 처리합니다.
     * <b>한 번도 배포된 적 없는 공지는 실패로 기록됩니다</b> — 그건 최초 검수라
     * 발행 여부를 사람이 따로 판단해야 합니다.
     */
    @PostMapping("/bulk/review-complete")
    public ApiResponse<BulkResult> bulkReviewComplete(@Valid @RequestBody ArticleIdsRequest request) {
        return ApiResponse.success(adminArticleService.completeReview(request.ids(), request.memo()));
    }

    /**
     * ADM-13 중복 공지 병합.
     *
     * <p>흡수된 공지는 <b>삭제됩니다.</b> 딸린 북마크·좋아요·댓글·첨부·출처는 대상으로 옮겨지고,
     * 응답의 {@code moved} 에 무엇이 몇 건 옮겨졌는지 나갑니다 —
     * 되돌릴 수 없는 조작이라 그 자리에서 확인할 수 있어야 합니다.
     */
    @PostMapping("/merge")
    public ApiResponse<MergeResult> merge(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MergeArticlesRequest request) {
        return ApiResponse.success(mergeService.merge(
                request.targetId(), request.sourceIds(), request.memo(), principal.userId()));
    }

    /**
     * SYS-07 AI 요약 재생성.
     *
     * <p>요약을 <b>지우기만</b> 합니다. 실제 생성은 주기 배치가 맡으므로 즉시 반영되지 않습니다.
     * 요약을 직접 고치려면 {@code PATCH /admin/articles/{id}} 를 쓰세요.
     */
    @PostMapping("/{articleId}/summary/regenerate")
    public ApiResponse<Void> regenerateSummary(@PathVariable Long articleId) {
        adminArticleService.regenerateSummary(articleId);
        return ApiResponse.success(null);
    }
}
