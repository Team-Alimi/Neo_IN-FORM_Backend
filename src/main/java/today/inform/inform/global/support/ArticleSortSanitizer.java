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

    /** 허용 정렬 기준. API 파라미터명 → 엔티티 프로퍼티명 */
    private static final Map<String, String> ALLOWED = Map.of(
            "published_at", "publishedAt",
            "bookmark_count", "bookmarkCount",
            "like_count", "likeCount",
            "ends_on", "endsOn",
            "created_at", "createdAt"
    );

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
            String property = ALLOWED.get(order.getProperty());
            if (property == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_SORT_PROPERTY,
                        "허용되지 않은 정렬 기준입니다: " + order.getProperty());
            }
            result = result.and(Sort.by(order.getDirection(), property));
        }

        // ★ tie-breaker 는 예외 없이 마지막에 붙인다.
        return result.and(Sort.by(Sort.Direction.DESC, TIE_BREAKER));
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
