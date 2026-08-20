package today.inform.inform.admin.article.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
import today.inform.inform.admin.article.dto.response.ReviewStats;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.article.repository.AdminArticleQueryRepository;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
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
    private final AuditMemo auditMemo;

    /** ADM-02 대시보드 카드. */
    @Transactional(readOnly = true)
    public ReviewStats stats() {
        return queryRepository.stats();
    }

    /** ADM-03 / ADM-12 목록. */
    @Transactional(readOnly = true)
    public Page<AdminArticleSummary> search(AdminArticleSearchCondition condition, Pageable pageable) {
        return queryRepository.search(condition, dropSort(pageable));
    }

    /** ADM-09 휴지통 목록. */
    @Transactional(readOnly = true)
    public Page<AdminArticleSummary> listTrashed(Pageable pageable) {
        return queryRepository.searchTrashed(dropSort(pageable));
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
     * ADM-07 상태 일괄 변경.
     *
     * <p><b>한 건이라도 전이가 불가능하면 전부 실패합니다.</b> 부분 성공을 허용하면
     * 관리자가 30건을 선택해 눌렀을 때 몇 건이 처리됐는지 화면에서 알 수 없고,
     * 다시 누르면 이미 처리된 것에 또 이력이 쌓입니다.
     * 전부 되돌리고 무엇이 문제인지 알려주는 편이 낫습니다.
     *
     * <p>엔티티를 하나씩 다루는 이유는 <b>전이 가능 여부가 공지마다 다르기</b> 때문입니다.
     * 현재 상태와 출처 유형에 따라 갈리므로 한 문장 UPDATE 로는 검사할 수 없습니다.
     */
    @Transactional
    public int changeStatus(List<Long> articleIds, ArticleStatus target, String memo) {
        // ★ 상태를 바꾸기 전에 넣어야 합니다. 트리거가 UPDATE 시점에 이 값을 읽습니다.
        auditMemo.set(memo);

        List<Article> articles = loadAll(articleIds);
        for (Article article : articles) {
            article.changeStatus(target);
        }
        return articles.size();
    }

    /**
     * 요청 목록을 정규화합니다.
     *
     * <p>중복을 걸러 내지 않으면 <b>존재하는 공지인데도 전 건이 404 가 됩니다.</b>
     * 아래 {@code loadAll} 이 조회 건수와 요청 길이를 비교하는데,
     * {@code IN (...)} 조회는 중복 id 를 한 행으로만 돌려주기 때문입니다.
     * 관리자 화면에서 같은 항목이 두 번 담기는 건 드문 일이 아닙니다.
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
    @Transactional
    public int moveToTrash(List<Long> articleIds, String memo) {
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
    @Transactional
    public int restore(List<Long> articleIds, String memo) {
        auditMemo.set(memo);

        List<Article> articles = loadAll(articleIds);
        Map<Long, ArticleStatus> previous =
                queryRepository.findPreviousStatuses(normalize(articleIds));

        for (Article article : articles) {
            ArticleStatus target = previous.get(article.getId());
            if (target == null) {
                throw new BusinessException(
                        ErrorCode.NOT_IN_TRASH,
                        "복구할 직전 상태를 찾을 수 없습니다. articleId=" + article.getId());
            }
            article.restoreTo(target);
        }
        return articles.size();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 요청한 공지를 전부 불러옵니다. <b>하나라도 없으면 실패합니다.</b>
     *
     * <p>조용히 건너뛰면 관리자가 30건을 선택했는데 28건만 처리되고,
     * 화면에는 성공으로 보입니다. 없는 번호가 섞여 있다는 건 화면과 DB 가
     * 어긋났다는 신호라 그대로 진행하면 안 됩니다.
     */
    private List<Article> loadAll(List<Long> articleIds) {
        List<Long> ids = normalize(articleIds);
        List<Article> articles = articleRepository.findAllById(ids);
        if (articles.size() != ids.size()) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return articles;
    }

    /**
     * 클라이언트 정렬을 버립니다.
     *
     * <p>관리자 목록은 "최근에 손댄 것" 순으로 고정입니다. 검수 화면에서 필요한 건
     * 무엇이 방금 바뀌었는가지 발행순이 아닙니다.
     * 받아 두면 Spring Data 가 검증 없이 이어 붙여 없는 속성 하나에 500 이 납니다.
     */
    private static Pageable dropSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
