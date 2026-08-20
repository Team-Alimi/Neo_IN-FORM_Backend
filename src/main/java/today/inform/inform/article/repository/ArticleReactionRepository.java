package today.inform.inform.article.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import today.inform.inform.article.entity.SourceType;

/**
 * 북마크·좋아요의 추가/해제.
 *
 * <h2>JPA 엔티티를 두지 않는 이유</h2>
 * 복합 PK 엔티티는 Spring Data {@code save()} 가 {@code merge()} 로 빠져
 * INSERT 전에 SELECT 가 한 번 더 나가고 {@code ON CONFLICT} 를 쓸 수 없습니다.
 * 이 테이블들은 관계 그 자체가 전부라 엔티티로 얻을 게 없습니다.
 * ({@code PreferenceType} 을 다루는 방식과 같습니다)
 *
 * <h2>왜 article 패키지에 있는가</h2>
 * 두 테이블의 개수를 세는 트리거가 {@code articles.bookmark_count} /
 * {@code like_count} 를 갱신합니다. 공지 집계의 일부라 공지 쪽에 둡니다.
 * bookmark·like 서비스가 이걸 함께 씁니다.
 *
 * <h2>카운터를 직접 건드리지 않는 이유</h2>
 * {@code UPDATE articles SET bookmark_count = ...} 를 앱이 같이 실행하면
 * 트리거가 올린 값에 한 번 더 더해집니다. 앱은 junction 행만 다루고
 * 개수는 DB 에 맡깁니다.
 */
@Repository
public class ArticleReactionRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 추가. <b>멱등입니다.</b>
     *
     * <p>{@code ON CONFLICT DO NOTHING} 이라 이미 있으면 조용히 넘어갑니다.
     * "있는지 조회 후 없으면 INSERT" 로 짜면 같은 사용자의 요청이 겹칠 때
     * 둘 다 조회를 통과해 한쪽이 PK 위반으로 실패합니다.
     * 더블클릭 한 번에 500 이 나가는 흔한 경로입니다.
     *
     * @return 실제로 추가됐으면 true. 이미 있었으면 false
     */
    public boolean add(ReactionType type, Long userId, Long articleId) {
        int inserted = em.createNativeQuery(
                        "INSERT INTO " + type.table() + " (user_id, article_id) "
                                + "VALUES (:userId, :articleId) ON CONFLICT DO NOTHING")
                .setParameter("userId", userId)
                .setParameter("articleId", articleId)
                .executeUpdate();
        return inserted > 0;
    }

    /**
     * 해제. 없어도 오류가 아닙니다.
     *
     * @return 실제로 지워졌으면 true
     */
    public boolean remove(ReactionType type, Long userId, Long articleId) {
        int deleted = em.createNativeQuery(
                        "DELETE FROM " + type.table() + " WHERE user_id = :userId AND article_id = :articleId")
                .setParameter("userId", userId)
                .setParameter("articleId", articleId)
                .executeUpdate();
        return deleted > 0;
    }

    /**
     * BMK-04 전체 삭제. {@code sourceType} 이 null 이면 전부 지웁니다.
     *
     * <p><b>한 문장으로 지웁니다.</b> 조회해서 건별로 지우면 북마크 200개에
     * DELETE 가 200번 나가고, 그때마다 카운터 트리거가 따로 돕니다.
     * 트리거는 행 단위라 어차피 200번 돌지만, 왕복은 한 번이면 됩니다.
     *
     * @return 지운 개수
     */
    public int removeAll(ReactionType type, Long userId, SourceType sourceType) {
        String sql = "DELETE FROM " + type.table() + " r WHERE r.user_id = :userId";
        if (sourceType != null) {
            sql += " AND EXISTS (SELECT 1 FROM articles a"
                    + " WHERE a.id = r.article_id AND a.source_type = :sourceType)";
        }

        var query = em.createNativeQuery(sql).setParameter("userId", userId);
        if (sourceType != null) {
            query.setParameter("sourceType", sourceType.name());
        }
        return query.executeUpdate();
    }
}
