package today.inform.inform.admin.article.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.dto.response.ArticleSummaryResponse.NamedRef;
import today.inform.inform.article.dto.response.DeadlineStatus;
import today.inform.inform.article.dto.response.VendorSummary;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;

/**
 * ADM-03 목록 한 줄 (명세 4.8 {@code AdminArticleSummary} = {@code ArticleSummary} + 관리자 축).
 *
 * <p><b>개인화 값({@code is_bookmarked}·{@code is_liked})은 넣지 않습니다.</b>
 * 검수 목록은 "누가 보느냐" 로 달라지는 화면이 아닙니다. 넣으려면 요청마다 관리자 신원을
 * 쿼리에 태워야 하는데, 아무도 쓰지 않는 값 때문에 검수 목록에 개인화 축을 만들 이유가 없습니다.
 *
 * @param similarArticleId 중복 의심 상대. <b>점수만으로는 병합 판단을 할 수 없습니다.</b>
 *                         "80% 유사" 라고만 하면 관리자가 무엇과 비교해야 할지 모릅니다
 * @param deadlineStatus   마감 기준 파생값. 사용자 목록과 같은 계산입니다
 * @param hasAttachment    첨부가 하나라도 있는지
 * @param createdBy        작성 관리자. 수집분은 {@code null} 입니다
 * @param lastStatusChange 상태가 마지막으로 바뀐 시각. {@code updatedAt}(내용 수정 시각)과 다릅니다 —
 *                         검수 화면은 "언제 손댔는가" 를 그것으로 판단합니다
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
        DeadlineStatus deadlineStatus,
        BigDecimal similarityScore,
        Long similarArticleId,
        OffsetDateTime updatedAt,
        OffsetDateTime lastStatusChange,
        Long createdBy,
        boolean hasAttachment,
        ArticleStatus previousStatus,
        List<VendorSummary> vendors,
        List<NamedRef> categories) {
}
