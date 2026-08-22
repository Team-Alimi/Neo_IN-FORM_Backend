package today.inform.inform.article.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
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
@RequiredArgsConstructor
public class ArticleService {

    private static final int POPULAR_MAX_LIMIT = 20;

    private final ArticleQueryRepository articleQueryRepository;
    private final ArticleQueryValidator queryValidator;
    private final ArticleViewCounter viewCounter;
    private final ArticleSummaryGenerator summaryGenerator;

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
        if (detail.summary() == null) {
            summaryGenerator.requestSummary(articleId, null);
        }
        return detail;
    }

}
