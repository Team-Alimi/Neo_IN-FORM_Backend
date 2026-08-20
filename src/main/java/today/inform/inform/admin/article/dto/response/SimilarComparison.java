package today.inform.inform.admin.article.dto.response;

import java.math.BigDecimal;

/**
 * ADM-12 유사 공지 비교.
 *
 * <p>관리자가 병합 여부를 판단하려면 <b>두 글을 나란히</b> 봐야 합니다.
 * "85% 유사" 라는 숫자만으로는 같은 공지인지 비슷한 제목의 다른 공지인지 알 수 없습니다.
 *
 * @param similar 비교 대상. 판정되지 않았거나 상대 공지가 지워졌으면 {@code null} 입니다
 */
public record SimilarComparison(
        AdminArticleDetail article,
        AdminArticleDetail similar,
        BigDecimal similarityScore) {
}
