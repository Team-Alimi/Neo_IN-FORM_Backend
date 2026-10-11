package today.inform.inform.article.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.dto.response.ArticleSummaryResponse.NamedRef;
import today.inform.inform.article.dto.response.VendorSummary;
import today.inform.inform.article.entity.SourceType;

/**
 * ART-02 상세.
 *
 * @param summary      ART-07. <b>null 일 수 있습니다.</b> 비어 있는 이유는
 *                     {@code summaryStatus} 가 알려 줍니다 — 그걸 보고 분기하세요.
 * @param summaryStatus {@code summary} 가 왜 비었는지. {@link SummaryStatus} 참고.
 *                     {@code PENDING} 이면 "생성 중" 을 띄우고
 *                     {@code GET /articles/{id}/summary} 로 몇 번 다시 보면 채워집니다.
 *                     <b>상세 조회로 폴링하지 마세요</b> — 이 API 는 요약이 비어 있을 때마다
 *                     생성 요청을 다시 던져서 작업 큐를 폴링으로 채웁니다
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
        SummaryStatus summaryStatus,
        OffsetDateTime publishedAt,
        LocalDate startsOn,
        LocalDate endsOn,
        DeadlineStatus deadlineStatus,
        int bookmarkCount,
        int commentCount,
        long viewCount,
        boolean isBookmarked,
        boolean underReview,
        List<NamedRef> categories,
        List<Source> vendors,
        List<Attachment> attachments) {

    /**
     * {@code summaryStatus} 만 바꾼 사본.
     *
     * <p><b>상태는 조회 계층이 정할 수 없습니다.</b> 요약이 비었을 때 생성 요청이 실제로
     * 등록됐는지(→ {@code PENDING}) 아니면 기능이 꺼져 있는지(→ {@code NONE}) 는
     * 서비스만 압니다. 그래서 레포지토리는 {@code null} 로 두고 서비스가 여기서 채웁니다.
     *
     * <p>레코드라 수정이 안 되므로 사본을 만듭니다. 필드가 많아 번거롭지만,
     * 응답 객체를 가변으로 바꾸면 "어디서 채워졌는지" 를 추적할 수 없게 됩니다.
     */
    public ArticleDetailResponse withSummaryStatus(SummaryStatus status) {
        return new ArticleDetailResponse(
                id, sourceType, title, content, summary, status, publishedAt,
                startsOn, endsOn, deadlineStatus, bookmarkCount, commentCount, viewCount,
                isBookmarked, underReview, categories, vendors, attachments);
    }

    /**
     * 제공처 + 그 제공처의 원본 URL (명세 4.3).
     *
     * <p>{@code VendorSummary} 에 {@code source_url} 을 붙인 형태입니다.
     * 둘을 붙여 둬야 "어디서 온 글인지" 가 화면에서 이어집니다.
     *
     * <p><b>같은 제공처가 여러 번 나올 수 있습니다.</b> 재게시된 원본은 각자 다른 URL 을 가지므로
     * 목록 카드({@code vendors})와 달리 여기서는 중복을 남깁니다.
     */
    public record Source(Long id, String name, String initial, SourceType type, String sourceUrl,
                        List<VendorSummary.ClubTypeRef> clubTypes) {
    }

    public record Attachment(Long id, String fileUrl, String originalName,
                             String contentType, Long sizeBytes) {
    }
}
