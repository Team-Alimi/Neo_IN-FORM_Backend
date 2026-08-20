package today.inform.inform.article.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * "이 사용자가 볼 수 있는 공지인가" 한 가지만 판정합니다.
 *
 * <p><b>왜 별도 컴포넌트인가</b>
 * 북마크·좋아요·댓글이 모두 같은 판정을 필요로 합니다. 각자 확인하게 두면
 * 규칙이 네 곳에 흩어지고, 나중에 노출 기준이 바뀔 때 하나를 빠뜨립니다.
 * 빠뜨린 곳은 오류가 아니라 <b>미배포 공지에 반응이 달리는</b> 조용한 구멍이 됩니다.
 *
 * <p>{@code ArticleService} 를 주입받지 않는 이유는 순환 때문입니다 —
 * 공지 목록은 북마크 여부를 알아야 하고, 북마크는 공지 가시성을 알아야 합니다.
 * 판정만 떼어 두면 양쪽이 이것만 바라봅니다.
 *
 * <p>기준은 상세 조회와 같습니다. 상세를 열 수 있으면 저장·좋아요도 할 수 있어야 합니다 —
 * 재검수로 내려간 공지를 북마크 목록에서 열어 좋아요를 누르는 흐름이 막히면 안 됩니다.
 */
@Component
public class ArticleReadableChecker {

    private static final String SQL = """
            SELECT EXISTS (
                SELECT 1 FROM articles a
                 WHERE a.id = :articleId
                   AND (a.status = 'PUBLISHED'
                        OR (a.status = 'PENDING_REVIEW' AND a.published_at IS NOT NULL)))
            """;

    @PersistenceContext
    private EntityManager em;

    /**
     * @throws BusinessException 없거나 볼 수 없으면 {@code ARTICLE_NOT_FOUND}(404).
     *                           403 을 쓰면 "그 번호의 공지는 존재한다" 는 사실이 새어 나갑니다.
     */
    @Transactional(readOnly = true)
    public void requireReadable(Long articleId) {
        Boolean readable = (Boolean) em.createNativeQuery(SQL)
                .setParameter("articleId", articleId)
                .getSingleResult();
        if (!Boolean.TRUE.equals(readable)) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
    }
}
