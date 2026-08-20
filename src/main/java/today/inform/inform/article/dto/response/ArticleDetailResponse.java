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
 * @param sources      원본 게시물. 같은 공지가 여러 게시판에 올라오면 여러 건입니다.
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
        int bookmarkCount,
        int likeCount,
        int commentCount,
        long viewCount,
        boolean isBookmarked,
        boolean isLiked,
        boolean underReview,
        List<NamedRef> categories,
        List<Source> sources,
        List<Attachment> attachments) {

    /** 제공처 + 그 제공처의 원본 URL. 둘을 붙여 둬야 "어디서 온 글인지" 가 화면에서 이어집니다. */
    public record Source(Long vendorId, String vendorName, String sourceUrl) {
    }

    public record Attachment(Long id, String fileUrl, String originalName,
                             String contentType, Long sizeBytes) {
    }
}
