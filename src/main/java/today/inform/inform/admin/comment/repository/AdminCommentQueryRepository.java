package today.inform.inform.admin.comment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import today.inform.inform.admin.comment.dto.request.AdminCommentSearchCondition;
import today.inform.inform.admin.comment.dto.response.AdminCommentSummary;
import today.inform.inform.global.support.LikePattern;
import today.inform.inform.user.entity.UserStatus;

/**
 * ADM-17 댓글 관리 조회.
 *
 * <p>사용자용 조회({@code CommentRepository})와 나눈 이유는 <b>공지 경계</b>입니다.
 * 저쪽은 언제나 한 공지 안에서 원댓글·답글을 묶어 보여 주는데, 여기는 공지를 가로질러
 * "이 사람이 쓴 것" 이나 "이 단어가 든 것" 을 찾습니다. 조건이 겹치지 않아 한 쿼리로 합칠 수 없습니다.
 */
@Repository
public class AdminCommentQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 댓글 검색. <b>최신순 고정</b>입니다.
     *
     * <p>클라이언트 정렬을 아예 받지 않습니다. Spring Data 라면 {@code sort} 를 검증 없이
     * 이어 붙여 500 이 나겠지만, 여기는 native 라 그런 경로 자체가 없습니다.
     * 신고 대응은 방금 달린 것부터 보는 화면이라 다른 정렬이 필요하지 않습니다.
     */
    public Page<AdminCommentSummary> search(AdminCommentSearchCondition condition, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = buildWhere(condition, params);

        long total = count(where, params);
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Query query = em.createNativeQuery("""
                SELECT c.id            AS id,
                       c.article_id    AS article_id,
                       a.title         AS article_title,
                       c.parent_id     AS parent_id,
                       u.id            AS author_id,
                       u.email         AS author_email,
                       u.name          AS author_name,
                       u.status        AS author_status,
                       c.content       AS content,
                       c.deleted_at    AS deleted_at,
                       (SELECT count(*) FROM comments r WHERE r.parent_id = c.id) AS reply_count,
                       c.created_at    AS created_at
                  FROM comments c
                  JOIN users    u ON u.id = c.user_id
                  JOIN articles a ON a.id = c.article_id
                 WHERE
                """ + where + """
                 ORDER BY c.created_at DESC, c.id DESC
                 LIMIT :limit OFFSET :offset
                """);
        bind(query, params);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        return new PageImpl<>(toSummaries(declareScalars(query).getResultList()), pageable, total);
    }

    /**
     * 잠글 순서를 정합니다. <b>답글이 먼저, 그다음 원댓글</b>입니다.
     *
     * <p><b>왜 답글이 먼저인가</b> — 관리자가 글타래 전체를 골라 지웠을 때 결과가 달라집니다.
     * 원댓글부터 지우면 그 시점에는 답글이 남아 있어 "자리를 남기는" 쪽으로 판정되고,
     * 답글까지 지운 뒤에는 <b>아무것도 안 달린 빈 껍데기</b>가 목록에 남습니다.
     * 답글을 먼저 없애면 원댓글은 답글 없는 상태가 되어 행째로 사라지고, 글타래가 깔끔히 없어집니다.
     *
     * <p><b>왜 순서를 고정하는가</b> — 두 관리자가 겹치는 댓글을 동시에 지울 때
     * 잠그는 순서가 다르면 교착이 납니다. {@code parent_id} 는 불변이라
     * 어느 트랜잭션이 계산해도 같은 순서가 나옵니다.
     *
     * <p><b>다만 이 순서만으로는 교착이 막히지 않습니다.</b> 여기서 정하는 것은 comments 행 잠금의
     * 순서일 뿐이고, 삭제는 카운터 트리거를 통해 articles 행 잠금을 추가로 잡습니다.
     * 그쪽까지 안전해지는 것은 {@code AdminCommentService#lockAll} 이 <b>지우기 전에</b>
     * 대상 전부를 잠그기 때문입니다. 그 주석에 이유가 적혀 있습니다.
     */
    public List<Long> findDeletionOrder(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Number> rows = em.createNativeQuery("""
                        SELECT id FROM comments
                         WHERE id IN (:ids)
                         ORDER BY (parent_id IS NULL) ASC, id ASC
                        """)
                .setParameter("ids", commentIds)
                .getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String buildWhere(AdminCommentSearchCondition condition, Map<String, Object> params) {
        StringBuilder where = new StringBuilder("1 = 1");

        if (!condition.includeDeleted()) {
            where.append(" AND c.deleted_at IS NULL");
        }
        if (condition.articleId() != null) {
            where.append(" AND c.article_id = :articleId");
            params.put("articleId", condition.articleId());
        }
        if (condition.userId() != null) {
            where.append(" AND c.user_id = :userId");
            params.put("userId", condition.userId());
        }
        if (condition.hasKeyword()) {
            // 본문은 순수 텍스트라 pg_bigm 인덱스가 없습니다. 대상이 조건으로 좁혀지지 않으면
            // 전체 스캔이 되지만, 신고 대응은 보통 공지나 작성자와 함께 좁혀서 씁니다.
            where.append(" AND lower(c.content) LIKE :keyword ESCAPE '\\'");
            params.put("keyword",
                    LikePattern.contains(condition.keyword().trim().toLowerCase(Locale.ROOT)));
        }
        return where.toString();
    }

    private long count(String where, Map<String, Object> params) {
        Query query = em.createNativeQuery("""
                SELECT count(*)
                  FROM comments c
                  JOIN users    u ON u.id = c.user_id
                  JOIN articles a ON a.id = c.article_id
                 WHERE
                """ + where);
        bind(query, params);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** 쿼리에 실제로 등장하는 파라미터만 바인딩합니다. count 와 본문은 SELECT 절이 달라 목록이 다릅니다. */
    private void bind(Query query, Map<String, Object> params) {
        Set<String> declared = query.getParameters().stream()
                .map(Parameter::getName)
                .collect(Collectors.toSet());
        params.forEach((name, value) -> {
            if (declared.contains(name)) {
                query.setParameter(name, value);
            }
        });
    }

    /**
     * 컬럼 타입을 명시합니다.
     *
     * <p>native 결과를 {@code Object[]} 로 그냥 받으면 드라이버가 고른 타입이 그대로 옵니다.
     * {@code count(*)} 가 {@code BigInteger} 로 오는 것처럼, 캐스팅 지점마다 다른 타입을
     * 가정하게 되어 조용히 깨집니다.
     */
    @SuppressWarnings("unchecked")
    private static NativeQuery<Object[]> declareScalars(Query query) {
        return query.unwrap(NativeQuery.class)
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("article_id", StandardBasicTypes.LONG)
                .addScalar("article_title", StandardBasicTypes.STRING)
                .addScalar("parent_id", StandardBasicTypes.LONG)
                .addScalar("author_id", StandardBasicTypes.LONG)
                .addScalar("author_email", StandardBasicTypes.STRING)
                .addScalar("author_name", StandardBasicTypes.STRING)
                .addScalar("author_status", StandardBasicTypes.STRING)
                .addScalar("content", StandardBasicTypes.STRING)
                .addScalar("deleted_at", StandardBasicTypes.OFFSET_DATE_TIME)
                .addScalar("reply_count", StandardBasicTypes.INTEGER)
                .addScalar("created_at", StandardBasicTypes.OFFSET_DATE_TIME);
    }

    private static List<AdminCommentSummary> toSummaries(List<Object[]> rows) {
        return rows.stream().map(AdminCommentQueryRepository::toSummary).toList();
    }

    private static AdminCommentSummary toSummary(Object[] row) {
        Long parentId = (Long) row[3];
        return new AdminCommentSummary(
                (Long) row[0],
                (Long) row[1],
                (String) row[2],
                parentId,
                parentId != null,
                (Long) row[4],
                (String) row[5],
                (String) row[6],
                UserStatus.valueOf((String) row[7]) == UserStatus.WITHDRAWN,
                (String) row[8],
                row[9] != null,
                (Integer) row[10],
                (java.time.OffsetDateTime) row[11]);
    }
}
