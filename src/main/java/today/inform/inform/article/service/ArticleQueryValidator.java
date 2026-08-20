package today.inform.inform.article.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.support.ArticleSortSanitizer;

/**
 * 공지 목록 요청의 사전 검증.
 *
 * <p><b>왜 서비스마다 두지 않는가</b>
 * 피드(ART-01)와 북마크 목록(BMK-02)은 노출 기준만 다르고 필터·정렬 규약은 같습니다.
 * 각자 검증하게 두면 규칙이 늘 때마다 한쪽에 넣는 걸 빠뜨리고,
 * 그러면 <b>같은 {@code sort} 파라미터가 한 목록에서는 400 이고 다른 목록에서는 200</b> 이 됩니다.
 * 클라이언트가 두 화면에 같은 정렬 UI 를 쓰는 순간 드러납니다.
 *
 * <p>여기 있는 둘은 "느린 요청" 이 아니라 <b>보내면 안 되는 요청</b>입니다.
 * 한 글자 검색은 2-gram 인덱스가 후보를 못 좁혀 사실상 전수 확인이 되고,
 * 마감 필터 없는 마감순 정렬은 부분 인덱스를 벗어나 전체 정렬이 됩니다.
 */
@Component
public class ArticleQueryValidator {

    public void validate(ArticleSearchCondition condition, Pageable pageable) {
        validateKeyword(condition);
        validateDeadlineSort(condition, pageable);
    }

    private void validateKeyword(ArticleSearchCondition condition) {
        if (condition.hasKeyword()
                && condition.trimmedKeyword().length() < ArticleSearchCondition.MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }
    }

    private void validateDeadlineSort(ArticleSearchCondition condition, Pageable pageable) {
        if (ArticleSortSanitizer.isDeadlineSort(pageable.getSort()) && !condition.isDeadlineOnly()) {
            throw new BusinessException(
                    ErrorCode.INVALID_SORT_PROPERTY,
                    "마감 임박순은 has_deadline=true 와 함께만 사용할 수 있습니다.");
        }
    }
}
