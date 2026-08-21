package today.inform.inform.article.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.dto.response.ArticleSummaryResponse.NamedRef;
import today.inform.inform.article.entity.SourceType;

/**
 * ART-02 상세.
 *
 * @param summary      ART-07. <b>null 일 수 있습니다.</b> 프론트는 요약 박스를 생략하고,
 *                     서버가 뒤에서 생성을 시작합니다. 다음 진입 때 채워집니다.
 * @param underReview  크롤러가 본문 변경을 감지해 재검수 중인 상태.
 *                     북마크에서 들어온 사용자에게 "검수 중" 배지를 띄우기 위한 값입니다.
 * @param deadlineStatus 마감 기준 파생값. DB 컬럼이 아니라 조회 시각에 계산합니다
 * @param vendors      제공처. 같은 공지가 여러 게시판에 올라오면 여러 건입니다.
 *                     목록의 {@link VendorSummary} 에 원본 링크({@code sourceUrl})가 하나 더 붙은 모양입니다 —
 *                     이름을 {@code sources} 가 아니라 {@code vendors} 로 두는 이유는 목록과 상세에서
 *                     같은 것을 같은 이름으로 부르기 위해서입니다(명세 2.8).
 */
public record ArticleDetailResponse(
        Long id,
        SourceType sourceType,
        String title,
        String content,
        String summary,
        OffsetDateTime publishedAt,
        LocalDate startsOn,
        LocalDate endsOn,
        DeadlineStatus deadlineStatus,
        int bookmarkCount,
        int likeCount,
        int commentCount,
        long viewCount,
        boolean isBookmarked,
        boolean isLiked,
        boolean underReview,
        List<NamedRef> categories,
        List<Source> vendors,
        List<Attachment> attachments) {

    /**
     * 제공처 + 그 제공처의 원본 URL (명세 4.3).
     *
     * <p>{@code VendorSummary} 에 {@code source_url} 을 붙인 형태입니다.
     * 둘을 붙여 둬야 "어디서 온 글인지" 가 화면에서 이어집니다.
     *
     * <p><b>같은 제공처가 여러 번 나올 수 있습니다.</b> 재게시된 원본은 각자 다른 URL 을 가지므로
     * 목록 카드({@code vendors})와 달리 여기서는 중복을 남깁니다.
     */
    public record Source(Long id, String name, String initial, SourceType type, String sourceUrl) {
    }

    public record Attachment(Long id, String fileUrl, String originalName,
                             String contentType, Long sizeBytes) {
    }
}
