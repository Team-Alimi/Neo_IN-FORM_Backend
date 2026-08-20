package today.inform.inform.bookmark.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.article.repository.ArticleReactionRepository;
import today.inform.inform.article.repository.ReactionType;
import today.inform.inform.article.service.ArticleQueryValidator;
import today.inform.inform.article.service.ArticleReadableChecker;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * BMK-01 ~ BMK-04.
 *
 * <p>추가·해제는 <b>둘 다 멱등</b>입니다. 이미 저장한 공지를 다시 저장해도,
 * 저장하지 않은 공지를 해제해도 200 입니다.
 * 토글 버튼은 연타·재시도·화면 두 개 동시 조작이 일상이라, 상태가 이미 목표와 같으면
 * 성공으로 다루는 편이 클라이언트를 단순하게 만듭니다.
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final ArticleReactionRepository reactionRepository;
    private final ArticleQueryRepository articleQueryRepository;
    private final ArticleQueryValidator queryValidator;
    private final ArticleReadableChecker readableChecker;

    /**
     * BMK-01 추가.
     *
     * <p><b>볼 수 있는 공지인지 먼저 확인합니다.</b> 확인하지 않으면 존재하지 않거나
     * 아직 배포되지 않은 공지 번호로 요청했을 때 조용히 저장되거나 FK 위반이 나갑니다.
     * 전자는 목록에 뜨지 않는 유령 북마크가 되고, 후자는 번호를 하나씩 넣어 보는 것만으로
     * <b>미배포 공지의 존재를 알아낼 수 있게</b> 합니다.
     */
    @Transactional
    public void add(Long userId, Long articleId) {
        readableChecker.requireReadable(articleId);
        reactionRepository.add(ReactionType.BOOKMARK, userId, articleId);
    }

    /**
     * BMK-03 해제.
     *
     * <p>여기서는 노출 여부를 보지 않습니다. 저장해 둔 공지가 나중에 휴지통으로 갔더라도
     * 사용자는 자기 목록에서 치울 수 있어야 합니다.
     */
    @Transactional
    public void remove(Long userId, Long articleId) {
        reactionRepository.remove(ReactionType.BOOKMARK, userId, articleId);
    }

    /**
     * BMK-04 전체 삭제.
     *
     * @param sourceType null 이면 전부. 지정하면 그 출처의 북마크만
     * @return 지운 개수
     */
    @Transactional
    public int removeAll(Long userId, SourceType sourceType) {
        return reactionRepository.removeAll(ReactionType.BOOKMARK, userId, sourceType);
    }

    /**
     * BMK-02 목록.
     *
     * <p>피드와 필터·정렬은 같지만 노출 기준이 넓습니다 —
     * 재검수로 내려간 공지도 "검수 중" 표시와 함께 남깁니다.
     *
     * <p>검증은 피드와 <b>같은 컴포넌트</b>를 씁니다. 복사해 두면 규칙이 늘 때
     * 한쪽에 넣는 걸 빠뜨려 같은 파라미터가 한쪽만 400 이 됩니다.
     */
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> list(Long userId, ArticleSearchCondition condition, Pageable pageable) {
        queryValidator.validate(condition, pageable);
        return articleQueryRepository.searchBookmarked(condition, userId, pageable);
    }
}
