package today.inform.inform.article.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.entity.SourceType;

/**
 * 목록 한 칸 (명세 2.8 {@code ArticleSummary}).
 *
 * <p>{@code vendors}·{@code categories}·{@code hasAttachment} 는 <b>페이지 단위로 한 번에</b> 채웁니다.
 * 항목마다 조회하면 한 페이지에 수십 번의 추가 쿼리가 나갑니다.
 *
 * @param deadlineStatus 마감 기준 파생값. DB 컬럼이 아니라 조회 시각에 계산합니다
 *                       ({@link DeadlineStatus} 참조)
 * @param summary        AI 생성. <b>미생성이면 null</b> 이고 프론트는 요약 박스를 생략합니다
 * @param isBookmarked   ART-06. 로그인 사용자 기준. 비로그인이면 항상 false
 * @param hasAttachment  첨부가 하나라도 있는지. 목록에 클립 아이콘을 그리기 위한 값이라
 *                       개수나 목록까지는 내보내지 않습니다
 * @param underReview    <b>명세에 없는 확장</b>입니다. 크롤러가 원본 수정을 감지해 재검수로 내려간 공지에
 *                       "검수 중" 배지를 띄우기 위한 값입니다. 북마크 목록에서만 true 가 될 수 있고
 *                       일반 피드에는 그런 공지가 애초에 나오지 않습니다
 */
public record ArticleSummaryResponse(
        Long id,
        SourceType sourceType,
        String title,
        String summary,
        OffsetDateTime publishedAt,
        LocalDate startsOn,
        LocalDate endsOn,
        DeadlineStatus deadlineStatus,
        int bookmarkCount,
        int commentCount,
        long viewCount,
        boolean isBookmarked,
        boolean hasAttachment,
        boolean underReview,
        List<VendorSummary> vendors,
        List<NamedRef> categories) {

    /** 카테고리처럼 "id + 표시명" 뿐인 참조 (명세 2.8 {@code CategorySummary}). */
    public record NamedRef(Long id, String name) {
    }
}
