package today.inform.inform.article.dto.request;

import java.time.LocalDate;
import java.util.List;
import today.inform.inform.article.entity.SourceType;

/**
 * ART-01/03/04 목록 필터.
 *
 * <p>{@code interestOnly} 만 {@code Boolean} 입니다. 기본값이 <b>켜짐</b>이라
 * {@code boolean} 으로 두면 "파라미터를 안 보냄"과 "false 를 보냄"이 구분되지 않아
 * 토글을 끌 수가 없습니다.
 *
 * @param interestOnly null 이면 켜짐으로 봅니다. 온보딩이 최소 1개를 강제하므로
 *                     첫 화면부터 개인화가 체감됩니다.
 */
public record ArticleSearchCondition(
        SourceType sourceType,
        List<Long> categoryIds,
        List<Long> vendorIds,
        String keyword,
        Boolean interestOnly,
        LocalDate startsFrom,
        LocalDate endsTo,
        Boolean hasDeadline) {

    /** pg_bigm 은 2-gram 이라 1글자로는 인덱스가 후보를 좁히지 못합니다. */
    public static final int MIN_KEYWORD_LENGTH = 2;

    public boolean isInterestOnly() {
        return interestOnly == null || interestOnly;
    }

    public boolean isDeadlineOnly() {
        return Boolean.TRUE.equals(hasDeadline);
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    public String trimmedKeyword() {
        return keyword == null ? null : keyword.trim();
    }

    public boolean hasCategoryFilter() {
        return categoryIds != null && !categoryIds.isEmpty();
    }

    public boolean hasVendorFilter() {
        return vendorIds != null && !vendorIds.isEmpty();
    }
}
