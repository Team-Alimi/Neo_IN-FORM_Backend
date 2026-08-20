package today.inform.inform.admin.article.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.repository.AdminArticleQueryRepository;
import today.inform.inform.admin.article.repository.AdminArticleWriteRepository;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * ADM-04 상세 · ADM-05 수정 · ADM-06 생성.
 *
 * <p>검수 파이프라인({@code AdminArticleService})과 나눈 이유는 다루는 것이 다르기 때문입니다.
 * 저쪽은 <b>상태</b>를 옮기고 이쪽은 <b>내용</b>을 씁니다.
 * 상태를 여기서 바꾸지 않는 것도 그래서입니다 — 상태 변경은 전이 규칙과 감사 이력을 타야 하므로
 * {@code PATCH /admin/articles/status} 한 곳으로만 갑니다.
 */
@Service
@RequiredArgsConstructor
public class AdminArticleWriteService {

    private final ArticleRepository articleRepository;
    private final AdminArticleWriteRepository writeRepository;
    private final AdminArticleQueryRepository queryRepository;

    /** ADM-04 상세. 상태를 가리지 않습니다. */
    @Transactional(readOnly = true)
    public AdminArticleDetail getDetail(Long articleId) {
        return queryRepository.findDetail(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    /**
     * ADM-06 게시글 번호 중복 확인.
     *
     * <p><b>이 결과는 보장이 아닙니다.</b> 두 관리자가 같은 번호를 확인하면 둘 다 "사용 가능" 을 받고,
     * 저장에서 한 명이 실패합니다. 최종 판정은 저장 시점의 PK 제약(409)입니다.
     * 잠금을 걸어 보장하려면 확인 시점부터 저장까지 번호를 붙들고 있어야 하는데,
     * 관리자가 글을 쓰는 동안 내내 잠그는 셈이라 얻는 것보다 잃는 것이 큽니다.
     */
    @Transactional(readOnly = true)
    public boolean isIdAvailable(Long articleId) {
        return !writeRepository.existsById(articleId);
    }

    /**
     * ADM-06 생성.
     *
     * <p>번호를 지정했으면 native INSERT 를 쓰고 <b>시퀀스를 밀어올립니다.</b>
     * 그러지 않으면 나중에 크롤러가 그 번호에 도달했을 때 수집이 통째로 실패합니다
     * ({@code AdminArticleWriteRepository#bumpSequence} 참조).
     */
    @Transactional
    public AdminArticleDetail create(SaveArticleRequest request, Long adminId) {
        SourceType sourceType = request.sourceType() == null ? SourceType.SCHOOL : request.sourceType();
        ArticleStatus status = request.status() == null ? defaultStatus(sourceType) : request.status();
        requireCreatable(status);
        requireTotalRequest(request);

        Long articleId = (request.articleId() == null)
                ? createWithGeneratedId(sourceType, status, request, adminId)
                : createWithManualId(sourceType, status, request, adminId);

        writeRepository.replaceCategories(articleId, request.categoryIds());
        writeRepository.syncVendors(articleId, request.vendors());

        return getDetail(articleId);
    }

    /**
     * ADM-05 수정.
     *
     * <p><b>바꿀 수 있는 것은 내용·기간·분류·출처뿐입니다.</b>
     * 출처 유형은 트리거가 IN001 로 막고, 상태는 전이 규칙을 타야 해서 여기서 다루지 않습니다.
     * 요청에 그 값들이 들어와도 조용히 무시합니다 — 거부하면 화면이 전체 폼을 보내는
     * 흔한 구현에서 매번 400 이 나기 때문입니다.
     *
     * <p>제목·본문·기간이 실제로 바뀌면 DB 가 AI 요약을 무효화하고 수정 시각을 올립니다.
     * 앱은 따로 할 일이 없습니다.
     *
     * <p><b>★ 부분 수정이 아니라 전체 교체입니다.</b> 요청 본문이 곧 "이 공지의 최종 모습" 이고,
     * 기간을 비워 보내면 기간이 지워집니다. 관리자 편집 화면이 폼 전체를 보내는 것을 전제합니다.
     * 분류·출처는 <b>생략 자체를 막아</b>({@code @NotNull}) 실수로 지워지는 일이 없게 했습니다 —
     * 비우려면 빈 배열을 명시적으로 보내야 합니다.
     */
    @Transactional
    public AdminArticleDetail update(Long articleId, SaveArticleRequest request) {
        requireTotalRequest(request);

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        article.edit(request.title(), request.content(), request.startsOn(), request.endsOn());

        writeRepository.replaceCategories(articleId, request.categoryIds());
        writeRepository.syncVendors(articleId, request.vendors());

        return getDetail(articleId);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Long createWithGeneratedId(SourceType sourceType, ArticleStatus status,
                                       SaveArticleRequest request, Long adminId) {
        Article article = articleRepository.save(Article.createWithStatus(
                sourceType, status,
                request.title(), request.content(),
                request.startsOn(), request.endsOn(), adminId));
        return article.getId();
    }

    /**
     * 번호를 직접 지정하는 경로.
     *
     * <p>JPA 를 쓸 수 없어 native INSERT 입니다. 그래서 엔티티의 검증을 못 거치므로
     * 같은 규칙을 여기서 한 번 확인합니다 — 검증이 두 곳에 생기는 건 좋지 않지만,
     * 통과시키면 DB 가 23514 로 뭉뚱그려 거부해 어느 값이 문제인지 알 수 없게 됩니다.
     */
    private Long createWithManualId(SourceType sourceType, ArticleStatus status,
                                    SaveArticleRequest request, Long adminId) {
        if (!status.isAllowedFor(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_SOURCE);
        }
        if (request.startsOn() != null && request.endsOn() != null
                && request.startsOn().isAfter(request.endsOn())) {
            throw new BusinessException(ErrorCode.INVALID_ARTICLE_PERIOD);
        }

        Long articleId = request.articleId();
        writeRepository.insertWithId(articleId, sourceType, status,
                request.title(), request.content(),
                request.startsOn(), request.endsOn(), adminId);

        // ★ INSERT 가 성공한 뒤에 밀어올립니다. 먼저 밀면 중복으로 실패했을 때
        //   쓰이지도 않은 번호만큼 시퀀스가 건너뛰어집니다.
        writeRepository.bumpSequence(articleId);
        return articleId;
    }

    /**
     * 분류·출처는 생략할 수 없습니다.
     *
     * <p>Bean Validation({@code @NotNull})이 컨트롤러에서 먼저 막지만 여기서 한 번 더 봅니다.
     * 이 서비스는 나중에 배치나 관리 도구에서도 불릴 수 있고, 그 경로는 검증을 안 거칩니다.
     * 빠뜨린 채 통과하면 <b>오류 없이 분류와 출처가 지워집니다</b> —
     * 조용히 데이터가 사라지는 쪽이 400 을 받는 것보다 훨씬 나쁩니다.
     */
    private static void requireTotalRequest(SaveArticleRequest request) {
        if (request.categoryIds() == null || request.vendors() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "분류·출처 목록은 생략할 수 없습니다. 비우려면 빈 배열을 보내세요.");
        }
        if (request.articleId() != null && request.articleId() > SaveArticleRequest.MAX_MANUAL_ID) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "게시글 번호가 너무 큽니다.");
        }
    }

    /**
     * 휴지통 상태로는 만들 수 없습니다.
     *
     * <p>복구는 <b>감사 이력의 직전 상태</b>로만 갑니다. 처음부터 휴지통으로 만들면
     * {@code to_status='TRASHED'} 인 이력의 {@code from_status} 가 NULL 이라
     * 복구할 곳이 없습니다. <b>영영 꺼낼 수 없는 공지</b>가 만들어지고,
     * 목록에도 안 뜨니 관리자는 그런 게 있는 줄도 모릅니다.
     */
    private static void requireCreatable(ArticleStatus status) {
        if (status == ArticleStatus.TRASHED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_FOR_SOURCE, "휴지통 상태로는 공지를 만들 수 없습니다.");
        }
    }

    private static ArticleStatus defaultStatus(SourceType sourceType) {
        return sourceType == SourceType.CLUB ? ArticleStatus.DRAFT : ArticleStatus.PENDING_REVIEW;
    }
}
