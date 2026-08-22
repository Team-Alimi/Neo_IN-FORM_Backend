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
        boolean interestOnly,
        LocalDate startsFrom,
        LocalDate endsTo,
        Boolean hasDeadline) {

    /** pg_bigm 은 2-gram 이라 1글자로는 인덱스가 후보를 좁히지 못합니다. */
    public static final int MIN_KEYWORD_LENGTH = 2;

    /**
     * 관심 분류로 거를지. <b>기본은 끔</b>입니다.
     *
     * <p>기본을 켬으로 두면 프론트가 파라미터를 빠뜨렸을 때 사용자가 공지를
     * <b>못 보게</b> 됩니다. 이 필터에는 폴백이 없어서 관심 분류가 0개면 목록이 그냥 빕니다 —
     * 온보딩을 안 끝냈거나 설정에서 전부 해제한 사람이 그렇습니다.
     * 서버는 200 을 주므로 아무 데도 기록이 남지 않고, 사용자는 놓친 마감을 놓친 줄도 모릅니다.
     *
     * <p>반대 방향의 실수(끄고 부르기)는 공지가 많이 보이는 것뿐이라 눈에 띄고 되돌리기 쉽습니다.
     * 두 실패의 비용이 다르므로 안전한 쪽을 기본으로 둡니다.
     */
    public boolean isInterestOnly() {
        return interestOnly;
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
