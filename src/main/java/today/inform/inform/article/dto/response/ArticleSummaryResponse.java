package today.inform.inform.article.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.entity.SourceType;

/**
 * 목록 한 칸.
 *
 * <p>{@code vendors} 와 {@code categories} 는 <b>페이지 단위로 한 번에</b> 채웁니다.
 * 항목마다 조회하면 한 페이지에 40번의 추가 쿼리가 나갑니다.
 *
 * @param isBookmarked ART-06. 로그인 사용자 기준
 * @param underReview  재검수로 내려간 공지. 북마크 목록에서만 true 가 될 수 있고
 *                     프론트가 "검수 중" 배지를 답니다. 피드에는 애초에 나오지 않습니다.
 */
public record ArticleSummaryResponse(
        Long id,
        SourceType sourceType,
        String title,
        String summary,
        OffsetDateTime publishedAt,
        LocalDate startsOn,
        LocalDate endsOn,
        int bookmarkCount,
        int likeCount,
        int commentCount,
        long viewCount,
        boolean isBookmarked,
        boolean isLiked,
        boolean underReview,
        List<NamedRef> vendors,
        List<NamedRef> categories) {

    /** 목록·상세 공통. 제공처·카테고리처럼 "id + 표시명" 뿐인 참조. */
    public record NamedRef(Long id, String name) {
    }
}
