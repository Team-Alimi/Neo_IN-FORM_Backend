package today.inform.inform.article.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.dto.response.ArticleSummaryStatusResponse;
import today.inform.inform.article.dto.response.SummaryStatus;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.user.entity.UserRole;

/**
 * ART-01 ~ ART-07 사용자 조회.
 *
 * <p>관리자 화면(ADM-*)은 노출 기준·정렬·필터가 전부 달라 별도 서비스로 둡니다.
 * 한 서비스에 합치면 "관리자면 이 조건 빼고" 분기가 메서드마다 생기고,
 * 그 분기 하나를 빠뜨리면 <b>미배포 공지가 사용자에게 새어 나갑니다.</b>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArticleService {

    private static final int POPULAR_MAX_LIMIT = 20;

    private final ArticleQueryRepository articleQueryRepository;
    private final ArticleQueryValidator queryValidator;
    private final ArticleViewCounter viewCounter;
    private final ArticleSummaryGenerator summaryGenerator;

    /**
     * {@code isEnabled()} 만 읽습니다. 생성은 {@link #summaryGenerator} 가 합니다.
     *
     * <p>이게 없으면 요약 기능이 꺼져 있을 때도 {@code PENDING} 을 내보내게 되고,
     * 그러면 <b>모든 사용자가 모든 공지에서 헛 재조회를 반복합니다.</b>
     */
    private final ArticleSummaryClient summaryClient;

    /** ART-01 / 03 / 04 */
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> search(ArticleSearchCondition condition,
                                               Pageable pageable,
                                               AuthPrincipal principal) {
        queryValidator.validate(condition, pageable);
        return articleQueryRepository.search(condition, principal.userId(), pageable);
    }

    /** ART-05 인기 공지. */
    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> getPopular(AuthPrincipal principal, int limit) {
        int bounded = Math.clamp(limit, 1, POPULAR_MAX_LIMIT);
        // ★ 비로그인으로 열려 있는 경로라 principal 이 null 로 들어옵니다.
        //   저장소가 null 을 익명 id 로 바꿔 개인화 값을 전부 false 로 만듭니다.
        return articleQueryRepository.findPopular(principal == null ? null : principal.userId(), bounded);
    }

    /**
     * ART-02 / 06 / 07 상세.
     *
     * <p>없는 공지와 볼 수 없는 공지를 <b>똑같이 404</b> 로 다룹니다.
     * 403 을 쓰면 "그 번호의 공지가 존재하기는 한다" 는 사실이 새어 나갑니다.
     *
     * <p>{@code readOnly} 가 아닙니다 — 조회수 집계가 쓰기를 합니다.
     */
    @Transactional
    public ArticleDetailResponse getDetail(Long articleId, AuthPrincipal principal) {
        ArticleDetailResponse detail = articleQueryRepository
                .findDetail(articleId, principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 관리자 조회는 세지 않습니다. 검수하느라 여러 번 드나드는 게 인기도로 잡히면 안 됩니다.
        if (principal.role() == UserRole.USER) {
            viewCounter.recordView(articleId, principal.userId());
        }

        // 요약이 없으면 생성을 시작만 하고 기다리지 않습니다.
        // 기다리면 첫 진입이 수 초 걸리고, 그 대기가 모든 사용자에게 반복됩니다.
        return detail.withSummaryStatus(startSummaryIfMissing(articleId, detail.summary()));
    }

    /**
     * 요약이 없으면 생성을 걸고, 화면이 분기할 수 있게 그 결과를 상태로 돌려줍니다.
     *
     * <p><b>{@code PENDING} 은 "요청을 걸었다" 까지만 뜻합니다.</b> 실제 생성은 커밋 뒤에
     * 비동기로 시작하므로(생성기가 {@code afterCommit} 에 등록합니다) 여기서는 성공을
     * 확인할 수 없고, 큐가 꽉 차 거부되는 것도 여기서는 예외로 올라오지 않습니다.
     * 그래서 화면은 <b>정해진 횟수만 재조회하고 포기해야</b> 합니다.
     */
    private SummaryStatus startSummaryIfMissing(Long articleId, String summary) {
        if (summary != null) {
            return SummaryStatus.READY;
        }
        if (!summaryClient.isEnabled()) {
            // 기다려도 안 생깁니다. 재조회하지 말라고 알려 줘야 합니다.
            return SummaryStatus.NONE;
        }
        try {
            summaryGenerator.requestSummary(articleId, null);
            return SummaryStatus.PENDING;
        } catch (RuntimeException e) {
            log.warn("요약 생성 요청 실패. articleId={}", articleId);
            return SummaryStatus.NONE;
        }
    }

    /**
     * ART-07 요약만 조회. <b>"생성 중" 을 띄운 화면이 재조회할 때 쓰는 전용 경로입니다.</b>
     *
     * <p><b>여기서는 생성을 걸지 않습니다.</b> 상세 조회는 요약이 빌 때마다 생성 요청을
     * 다시 던지므로, 그걸로 폴링하면 작업 큐(용량 20, 초과 시 거부)가 폴링으로 채워져
     * <b>실제 생성 작업이 밀려 나갑니다.</b> 생성은 상세 조회 한 번이 이미 걸어 뒀습니다.
     *
     * <p>조회수도 세지 않습니다 — 재조회는 사용자의 열람이 아닙니다.
     * (상세 쪽은 30분 중복 제거가 있어 부풀지 않지만, 애초에 셀 일이 아닙니다)
     *
     * <p>가시성은 상세와 같아서, 상세로 404 인 공지는 여기서도 404 입니다.
     */
    @Transactional(readOnly = true)
    public ArticleSummaryStatusResponse getSummary(Long articleId) {
        ArticleQueryRepository.SummaryRow row = articleQueryRepository.findSummaryRow(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        if (row.summary() != null) {
            return ArticleSummaryStatusResponse.of(row.summary(), SummaryStatus.READY);
        }
        return ArticleSummaryStatusResponse.of(null,
                summaryClient.isEnabled() ? SummaryStatus.PENDING : SummaryStatus.NONE);
    }

}
