package today.inform.inform.admin.article.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.article.dto.response.DuplicateCandidate;
import today.inform.inform.admin.article.dto.response.ReviewStats;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.article.repository.AdminArticleQueryRepository;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.article.service.ArticleSummaryGenerator;
import today.inform.inform.global.support.AuditMemo;

/**
 * ADM-02 · 03 · 07 · 08 · 09 · 11 — 공지 검수 파이프라인.
 *
 * <h2>감사 로그가 이 서비스의 절반입니다</h2>
 * 상태 변경은 전부 DB 트리거가 {@code article_status_logs} 에 기록합니다.
 * 앱이 할 일은 <b>"누가" 와 "왜" 를 트랜잭션에 알려주는 것</b>뿐입니다.
 * <ul>
 *   <li>행위자 — {@code AuditAwareTransactionManager} 가 트랜잭션 시작 시 자동 주입</li>
 *   <li>사유 — 요청마다 다르므로 {@link AuditMemo} 로 <b>상태 변경 전에</b> 넣어야 합니다</li>
 * </ul>
 * 사유를 빠뜨리면 오류가 아니라 {@code memo = NULL} 로 남습니다.
 * 행위자를 빠뜨리면 {@code changed_by = NULL} 이 되는데,
 * 그건 스키마상 "크롤러/시스템이 한 변경" 의 정상값이라 <b>사후에 사고와 구분할 수 없습니다.</b>
 */
@Service
@RequiredArgsConstructor
public class AdminArticleService {

    private final AdminArticleQueryRepository queryRepository;
    private final ArticleRepository articleRepository;
    private final ArticleSummaryGenerator summaryGenerator;
    private final BulkExecutor bulkExecutor;
    private final AuditMemo auditMemo;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    /** ADM-02 대시보드 카드. */
    @Transactional(readOnly = true)
    public ReviewStats stats() {
        return queryRepository.stats();
    }

    /** ADM-03 / ADM-12 목록. */
    @Transactional(readOnly = true)
    public Page<AdminArticleSummary> search(AdminArticleSearchCondition condition, Pageable pageable) {
        return queryRepository.search(condition, pageable);
    }

    /** ADM-09 휴지통 목록. */
    @Transactional(readOnly = true)
    public Page<AdminArticleSummary> listTrashed(Pageable pageable) {
        return queryRepository.searchTrashed(pageable);
    }

    /**
     * ADM-12 중복 확인.
     *
     * <p>수기 등록 전에 같은 원본이 이미 들어와 있는지 봅니다.
     * 조건을 하나도 안 주면 빈 결과입니다 — 조건 없이 전체를 훑을 이유가 없습니다.
     */
    @Transactional(readOnly = true)
    public DuplicateCandidate.Result findDuplicates(String externalKey, String title, int limit) {
        return DuplicateCandidate.Result.of(
                queryRepository.findDuplicates(externalKey, title, limit));
    }

    /** ADM-11 상태 변경 이력. */
    @Transactional(readOnly = true)
    public List<StatusLogResponse> statusLogs(Long articleId) {
        if (!articleRepository.existsById(articleId)) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return queryRepository.findStatusLogs(articleId);
    }

    /**
     * ADM-06 상태 일괄 변경 — {@code POST /admin/articles/bulk/status}.
     *
     * <h2>부분 성공입니다 (명세 4.8 공통 규약)</h2>
     * 관리자가 30건을 골랐는데 하나가 전이 규칙에 걸렸다고 나머지 29건까지 되돌리면,
     * 관리자는 <b>어느 것이 문제였는지 목록에서 직접 찾아야</b> 합니다.
     * 실패한 건만 사유와 함께 돌려주면 그 자리에서 알 수 있습니다.
     *
     * <p><b>{@code @Transactional} 을 붙이면 안 됩니다.</b> 여기에 트랜잭션을 걸면
     * {@code BulkExecutor} 의 건별 트랜잭션이 그 안으로 합쳐지고, 한 건이 실패하는 순간
     * 전체가 rollback-only 로 표시되어 <b>부분 성공이 조용히 무너집니다.</b>
     *
     * <p>엔티티를 하나씩 다루는 이유는 <b>전이 가능 여부가 공지마다 다르기</b> 때문입니다.
     * 현재 상태와 출처 유형에 따라 갈리므로 한 문장 UPDATE 로는 검사할 수 없습니다.
     */
    public BulkResult changeStatus(List<Long> articleIds, ArticleStatus target, String memo) {
        return bulkExecutor.runEach(articleIds, id -> {
            // ★ 상태를 바꾸기 전에 넣어야 합니다. 트리거가 UPDATE 시점에 이 값을 읽습니다.
            //   건별 트랜잭션이므로 건마다 새로 넣어야 합니다 — GUC 는 트랜잭션-로컬입니다.
            auditMemo.set(memo);
            load(id).changeStatus(target);
        });
    }

    /** ADM-07 / CLB-03 배포 — {@code POST /admin/articles/bulk/publish}. */
    public BulkResult publish(List<Long> articleIds, String memo) {
        return changeStatus(articleIds, ArticleStatus.PUBLISHED, memo);
    }

    /**
     * 요청 목록을 정규화합니다.
     *
     * <p>같은 항목이 두 번 담긴 요청에서 두 번째가 "이미 처리됨" 으로 실패해 보이지 않도록
     * 중복을 걸러 냅니다 — 관리자 화면에서 같은 항목이 두 번 선택되는 건 드문 일이 아닙니다.
     * ({@code BulkExecutor} 도 같은 정규화를 하므로 이쪽은 이력 조회용입니다)
     *
     * <p>{@code null} 원소도 여기서 걸러 냅니다 — 그대로 두면 바인딩 단계에서
     * 원인을 알 수 없는 오류가 납니다.
     */
    private static List<Long> normalize(List<Long> articleIds) {
        return articleIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * ADM-08 휴지통 이동.
     *
     * <p>어느 상태에서든 갈 수 있어 전이 검사가 사실상 없지만,
     * <b>현재 상태를 이력에 남기는 것</b>이 핵심입니다. 그래야 복구할 때
     * 어디로 되돌릴지 알 수 있습니다. 기록은 트리거가 하므로 앱은 상태만 바꿉니다.
     */
    public BulkResult moveToTrash(List<Long> articleIds, String memo) {
        return changeStatus(articleIds, ArticleStatus.TRASHED, memo);
    }

    /**
     * ADM-09 복구.
     *
     * <p><b>임의 상태로 보낼 수 없습니다.</b> 이력이 기억하는 직전 상태로만 갑니다.
     * 관리자가 목적지를 고를 수 있게 하면 휴지통이 상태 전이 규칙의 우회로가 됩니다 —
     * 검수 대기 공지를 휴지통에 넣었다가 바로 배포 상태로 꺼낼 수 있게 됩니다.
     *
     * <p>이력이 없는 공지는 복구할 수 없습니다. 휴지통 이동이 앱을 거치지 않았다는 뜻이라
     * 어디로 되돌릴지 알 방법이 없습니다.
     */
    public BulkResult restore(List<Long> articleIds, String memo) {
        // 직전 상태는 건별 트랜잭션 밖에서 한 번에 읽습니다 — 건마다 읽으면 200 번 나갑니다.
        // 이 값은 이력이라 그 사이 바뀌지 않습니다.
        Map<Long, ArticleStatus> previous =
                queryRepository.findPreviousStatuses(normalize(articleIds));

        return bulkExecutor.runEach(articleIds, id -> {
            auditMemo.set(memo);

            ArticleStatus target = previous.get(id);
            if (target == null) {
                throw new BusinessException(
                        ErrorCode.NOT_IN_TRASH,
                        "복구할 직전 상태를 찾을 수 없습니다. articleId=" + id);
            }
            load(id).restoreTo(target);
        });
    }

    /**
     * ADM-03a 재검수 완료.
     *
     * <h2>명세가 말하는 컬럼은 이제 없습니다</h2>
     * 명세는 {@code review_requested_at} 를 NULL 로 지우라고 적고 있지만,
     * 그 컬럼은 스키마에서 <b>제거됐습니다</b>({@code SCHEMA_STATUS} 3장 2번).
     * 재검수는 "노출 유지 + 플래그" 에서 <b>"검수 대기로 강등"</b> 으로 정책이 바뀌었고,
     * 그래서 재검수 대기란 곧 {@code PENDING_REVIEW 이면서 published_at 이 있는} 상태입니다.
     *
     * <h2>왜 배포까지 한 번에 하는가</h2>
     * 이 공지는 <b>이미 사용자에게 보이던 것</b>인데 크롤러가 원본 수정을 감지해 내려간 상태입니다.
     * 즉 지금 이 순간에도 피드에서 사라져 있습니다. 검수를 통과시켰는데 발행 대기에 두면
     * 누군가 한 번 더 누를 때까지 <b>그 공백이 계속됩니다.</b>
     *
     * <p>전이 규칙을 우회하지는 않습니다. {@code PENDING_REVIEW → READY_TO_PUBLISH → PUBLISHED} 를
     * 순서대로 밟으므로 두 단계 모두 규칙 검사를 거치고, 감사 이력에도 두 줄이 남습니다.
     * 실제로 일어난 일이 두 단계이므로 그게 사실입니다.
     *
     * <p><b>★ 그래서 중간에 flush 가 필요합니다.</b> 두 번의 {@code changeStatus} 를 그냥 이어서 부르면
     * 더티 체킹이 <b>마지막 값 하나로 UPDATE 를 합칩니다.</b> 그러면 DB 가 보는 것은
     * {@code PENDING_REVIEW → PUBLISHED} 라는 <b>전이 표에 없는 점프</b> 하나뿐이고,
     * 감사 로그에도 그 한 줄만 남습니다. 앱은 두 단계를 검사했다고 믿는데
     * 기록은 규칙 위반처럼 보이는, 앞뒤가 안 맞는 상태가 됩니다.
     * (DB 에는 전이 표 제약이 없어서 아무 오류도 나지 않습니다 — 그래서 더 위험합니다)
     *
     * <h2>한 번도 배포된 적 없는 공지는 거부합니다</h2>
     * 그건 재검수가 아니라 <b>최초 검수</b>이고, 사람이 발행 여부를 따로 판단해야 합니다.
     * 여기서 통과시키면 "재검수 완료" 버튼 하나로 신규 수집분이 바로 배포됩니다.
     */
    public BulkResult completeReview(List<Long> articleIds, String memo) {
        return bulkExecutor.runEach(articleIds, id -> {
            auditMemo.set(memo);

            Article article = load(id);
            requireUnderReview(article);
            article.changeStatus(ArticleStatus.READY_TO_PUBLISH);

            // ★ 여기서 끊어야 두 전이가 각각 UPDATE 로 나갑니다. 위 javadoc 참조.
            em.flush();

            article.changeStatus(ArticleStatus.PUBLISHED);
        });
    }

    /**
     * SYS-07 요약 재생성.
     *
     * <p>요약을 직접 만들지 않고 <b>지우기만</b> 합니다. 생성은 {@code summary IS NULL} 을 훑는
     * 주기 배치가 맡습니다 — 관리자를 LLM 응답만큼 기다리게 할 이유가 없습니다.
     *
     * <p><b>native UPDATE 여야 합니다.</b> {@code summary} 는 엔티티에서
     * {@code updatable = false} 라 JPA 로는 쓸 수 없고, 그렇게 둔 이유가 있습니다 —
     * {@code updated_at} 화이트리스트에 {@code summary} 가 없으므로
     * 이 UPDATE 는 <b>수정 시각도 version 도 건드리지 않습니다.</b>
     * 요약을 다시 만든 것이 "공지가 수정됨" 으로 보이면 안 됩니다.
     *
     * <p>지운 뒤 생성기를 깨워 두지만 기다리지는 않습니다. 배치가 어차피 주워 가므로
     * 이 호출이 실패해도 다음 주기에 만들어집니다.
     */
    @Transactional
    public void regenerateSummary(Long articleId) {
        if (!articleRepository.existsById(articleId)) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        em.createNativeQuery("UPDATE articles SET summary = NULL WHERE id = :id")
                .setParameter("id", articleId)
                .executeUpdate();

        summaryGenerator.requestSummary(articleId, null);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 재검수 대기 상태인지.
     *
     * <p>{@code published_at} 이 "한 번이라도 배포된 적 있음" 의 표식입니다
     * ({@code ArticleQueryRepository.VISIBLE_OR_UNDER_REVIEW} 가 같은 판정을 씁니다).
     * DB CHECK 가 {@code status='PUBLISHED'} 일 때 이 값을 강제하므로,
     * 배포를 거친 공지는 이후 어떤 상태로 가도 이 표식을 잃지 않습니다.
     */
    private static void requireUnderReview(Article article) {
        boolean underReview = article.getStatus() == ArticleStatus.PENDING_REVIEW
                && article.getPublishedAt() != null;
        if (!underReview) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "재검수 대기 중인 공지가 아닙니다. 신규 수집분은 일반 검수 흐름으로 처리해 주세요. "
                            + "articleId=" + article.getId());
        }
    }

    /**
     * 요청한 공지를 전부 불러옵니다. <b>하나라도 없으면 실패합니다.</b>
     *
     * <p>조용히 건너뛰면 관리자가 30건을 선택했는데 28건만 처리되고,
     * 화면에는 성공으로 보입니다. 없는 번호가 섞여 있다는 건 화면과 DB 가
     * 어긋났다는 신호라 그대로 진행하면 안 됩니다.
     */
    /** 벌크 한 건. 없으면 그 건만 실패로 기록됩니다 — 나머지는 계속 처리됩니다. */
    private Article load(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

}
