package today.inform.inform.admin.article.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.dto.response.ArticleSummaryResponse.NamedRef;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;

/**
 * ADM-03 목록 한 줄. 화면 컬럼이 그대로 들어 있습니다 —
 * 게시글 ID · 카테고리 · 상태 · 제목 · 행사 기간 · 출처 · 최종 수정일.
 *
 * @param similarArticleId 중복 의심 상대. <b>점수만으로는 병합 판단을 할 수 없습니다.</b>
 *                         "80% 유사" 라고만 하면 관리자가 무엇과 비교해야 할지 모릅니다
 * @param previousStatus   휴지통 목록에서만 채웁니다. 휴지통에 들어가기 <b>직전</b> 상태입니다 —
 *                         {@code articles.status} 는 TRASHED 로 덮여 있어 이력에서 가져옵니다
 */
public record AdminArticleSummary(
        Long id,
        SourceType sourceType,
        ArticleStatus status,
        String title,
        LocalDate startsOn,
        LocalDate endsOn,
        BigDecimal similarityScore,
        Long similarArticleId,
        OffsetDateTime updatedAt,
        ArticleStatus previousStatus,
        List<NamedRef> vendors,
        List<NamedRef> categories) {
}
