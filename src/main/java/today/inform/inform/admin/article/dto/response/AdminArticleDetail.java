package today.inform.inform.admin.article.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.entity.SourceType;

/**
 * ADM-04 관리자 상세.
 *
 * <p>사용자 상세({@code ArticleDetailResponse})와 다른 점 —
 * 상태·유사도·작성자처럼 <b>검수에 필요한 값</b>이 있고,
 * 개인화 값({@code is_bookmarked} 등)은 없습니다. 관리자에게는 의미가 없습니다.
 *
 * @param vendors 원본 식별자({@code externalKey})까지 내려줍니다.
 *                관리자가 "이건 크롤러가 수집한 출처라 지우면 안 된다" 를 화면에서 구분해야 합니다
 */
public record AdminArticleDetail(
        Long id,
        SourceType sourceType,
        ArticleStatus status,
        String title,
        String content,
        String summary,
        LocalDate startsOn,
        LocalDate endsOn,
        OffsetDateTime publishedAt,
        BigDecimal similarityScore,
        Long similarArticleId,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<CategoryRef> categories,
        List<VendorRef> vendors,
        List<ArticleDetailResponse.Attachment> attachments,
        List<StatusLogResponse> statusLogs) {

    public record CategoryRef(Long id, String name) {
    }

    /**
     * @param externalKey 크롤러가 넣은 원본 게시판 글 번호.
     *                    <b>{@code null} 이 아니면 크롤러 수집분</b>이라 지우면
     *                    다음 수집에서 같은 공지가 새로 만들어집니다
     */
    public record VendorRef(Long id, Long vendorId, String vendorName,
                            String sourceUrl, String externalKey) {
    }
}
