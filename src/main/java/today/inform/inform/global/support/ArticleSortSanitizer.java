package today.inform.inform.global.support;

import java.util.Map;
import org.springframework.data.domain.Sort;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 공지 목록 정렬을 화이트리스트로 검증하고 <b>tie-breaker 를 강제</b>한다.
 *
 * <p><b>왜 id 를 반드시 붙이는가</b>
 * {@code now()} 는 트랜잭션 안에서 고정값이므로, 관리자가 30건을 일괄 배포하면
 * 30건 전부 {@code published_at} 이 완전히 같아진다. 정렬 키가 유일하지 않으면
 * 같은 값끼리 순서가 정해지지 않아 <b>페이지 경계에서 같은 공지가 두 번 나오거나
 * 아예 누락된다.</b> 카운터 정렬(bookmark_count 등)은 값이 0~5 에 몰려 더 심하다.
 *
 * <p>DB 인덱스도 {@code (published_at DESC, id DESC)} 형태로 만들어 두었으므로
 * 여기서 붙이는 순서와 일치해야 인덱스를 탄다.
 *
 * <p>API 는 snake_case 를 받고 JPA 는 camelCase 프로퍼티를 쓰므로 이름을 변환한다.
 */
public final class ArticleSortSanitizer {

    /**
     * 허용 정렬 기준. API 파라미터명 → (엔티티 프로퍼티명, 컬럼명)
     *
     * <p>목록 조회는 native SQL 이라 컬럼명이 필요하고, JPA 경로는 프로퍼티명이 필요합니다.
     * 두 곳에 따로 적으면 한쪽만 늘어나 "JPA 로는 되는데 목록에서는 400" 같은 일이 생깁니다.
     */
    private static final Map<String, Allowed> ALLOWED = Map.of(
            "published_at",   new Allowed("publishedAt",   "published_at"),
            "bookmark_count", new Allowed("bookmarkCount", "bookmark_count"),
            "ends_on",        new Allowed("endsOn",        "ends_on"),
            "created_at",     new Allowed("createdAt",     "created_at"),
            "updated_at",     new Allowed("updatedAt",     "updated_at")
    );

    private record Allowed(String property, String column) {
    }

    /** 마감 임박순은 마감일 있는 공지 필터와 함께만 허용한다 (인덱스가 그 조건에서만 동작) */
    public static final String DEADLINE_SORT = "ends_on";

    private static final String TIE_BREAKER = "id";
    private static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "publishedAt", TIE_BREAKER);

    private ArticleSortSanitizer() {
    }

    /**
     * @param requested 클라이언트가 보낸 정렬. 비어 있으면 최신순 기본값을 쓴다.
     * @return 항상 마지막이 {@code id DESC} 인 정렬
     * @throws BusinessException 허용되지 않은 정렬 기준이면 {@code INVALID_SORT_PROPERTY}
     */
    public static Sort sanitize(Sort requested) {
        if (requested == null || requested.isUnsorted()) {
            return DEFAULT;
        }

        Sort result = Sort.unsorted();
        for (Sort.Order order : requested) {
            result = result.and(Sort.by(order.getDirection(), require(order).property()));
        }

        // ★ tie-breaker 는 예외 없이 마지막에 붙인다.
        return result.and(Sort.by(Sort.Direction.DESC, TIE_BREAKER));
    }

    /**
     * native SQL 용 {@code ORDER BY} 절을 만든다. 별칭 {@code a} 를 가정한다.
     *
     * <p>문자열을 SQL 에 직접 이어 붙이지만 인젝션 경로가 없다 —
     * 컬럼명은 {@link #ALLOWED} 의 상수이고 방향은 enum 이다.
     * 클라이언트 문자열이 그대로 들어가는 지점이 한 곳도 없다.
     *
     * @return 예: {@code "a.published_at DESC, a.id DESC"}
     */
    public static String toSqlOrderBy(Sort requested) {
        return toSqlOrderBy(requested, "published_at");
    }

    /**
     * @param defaultColumn 정렬을 안 보냈을 때 쓸 컬럼. 사용자 목록은 발행순,
     *                      관리자 검수 목록은 <b>생성순</b>이 기본입니다(명세 4.8) —
     *                      두 화면이 보는 것이 다릅니다
     */
    public static String toSqlOrderBy(Sort requested, String defaultColumn) {
        if (requested == null || requested.isUnsorted()) {
            return "a." + defaultColumn + " DESC, a." + TIE_BREAKER + " DESC";
        }

        StringBuilder clause = new StringBuilder();
        for (Sort.Order order : requested) {
            clause.append("a.").append(require(order).column())
                    .append(order.isAscending() ? " ASC" : " DESC")
                    // NULL 이 있는 컬럼(ends_on)에서 DB 기본값에 맡기면 오름차순일 때
                    // NULL 이 뒤로 가고 내림차순일 때 앞으로 온다. 마감 없는 공지가
                    // 마감 임박 목록 맨 위에 뜨는 걸 막으려면 명시해야 한다.
                    .append(" NULLS LAST, ");
        }
        return clause.append("a.").append(TIE_BREAKER).append(" DESC").toString();
    }

    private static Allowed require(Sort.Order order) {
        Allowed allowed = ALLOWED.get(order.getProperty());
        if (allowed == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_SORT_PROPERTY,
                    "허용되지 않은 정렬 기준입니다: " + order.getProperty());
        }
        return allowed;
    }

    /**
     * 마감 임박순 요청인지 판정한다.
     * 서비스는 이 경우 "마감일 있는 공지" 필터가 함께 왔는지 확인해야 한다.
     */
    public static boolean isDeadlineSort(Sort requested) {
        if (requested == null || requested.isUnsorted()) {
            return false;
        }
        for (Sort.Order order : requested) {
            if (DEADLINE_SORT.equals(order.getProperty())) {
                return true;
            }
        }
        return false;
    }
}
