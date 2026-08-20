package today.inform.inform.article.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import today.inform.inform.article.dto.request.ArticleSearchCondition;
import today.inform.inform.article.dto.response.ArticleDetailResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.dto.response.ArticleSummaryResponse.NamedRef;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.support.ArticleSortSanitizer;

/**
 * 공지 <b>조회</b> 저장소. 쓰기는 {@link ArticleRepository} 가 담당합니다.
 *
 * <h2>왜 엔티티가 아니라 DTO 를 직접 만드는가</h2>
 * 목록 한 칸에는 {@code is_bookmarked}, 제공처 이름, 카테고리 이름이 함께 필요합니다.
 * 엔티티로 받아 서비스에서 채우면 20건짜리 한 페이지에 추가 쿼리가 수십 번 나갑니다.
 * 개인화 값은 같은 쿼리의 {@code EXISTS} 로, 이름 목록은 <b>페이지 단위 한 번</b>으로 가져옵니다.
 * 목록 한 페이지에 나가는 쿼리는 총 4개(count · 본문 · 제공처 · 카테고리)로 고정입니다.
 *
 * <h2>왜 native SQL 인가</h2>
 * <ul>
 *   <li>{@code likequery()} — pg_bigm 전용 함수입니다. JPQL 로 표현할 방법이 없습니다.</li>
 *   <li>복수 필터는 JOIN 이 아니라 {@code EXISTS} 여야 합니다. JOIN 하면 같은 공지가
 *       카테고리 수만큼 중복되고, {@code DISTINCT} 로 막으면 페이지 크기와
 *       {@code total_elements} 가 둘 다 어긋납니다.</li>
 * </ul>
 *
 * <h2>인젝션</h2>
 * SQL 을 문자열로 조립하지만 클라이언트 값이 들어가는 자리는 전부 bind parameter 입니다.
 * 이어 붙이는 건 고정 문자열과 {@link ArticleSortSanitizer} 의 화이트리스트 컬럼명뿐입니다.
 */
@Repository
public class ArticleQueryRepository {

    /** 서비스 목록 노출 기준. */
    private static final String VISIBLE = "a.status = 'PUBLISHED'";

    /**
     * 상세·북마크 목록의 노출 기준. 목록보다 넓습니다.
     *
     * <p>크롤러가 배포된 공지의 오탈자를 고치면 재검수 대기로 내려갑니다.
     * 목록 기준을 그대로 쓰면 <b>사용자가 저장해 둔 공지를 클릭했을 때 404</b> 가 납니다.
     * {@code published_at IS NOT NULL} 이 "한 번 배포된 적 있음" 표식이라
     * 아직 한 번도 배포된 적 없는 신규 수집분은 자동으로 걸러집니다.
     */
    private static final String VISIBLE_OR_UNDER_REVIEW =
            "(a.status = 'PUBLISHED' OR (a.status = 'PENDING_REVIEW' AND a.published_at IS NOT NULL))";

    private static final String SUMMARY_COLUMNS = """
            a.id                AS id,
            a.source_type       AS source_type,
            a.title             AS title,
            a.summary           AS summary,
            a.published_at      AS published_at,
            a.starts_on         AS starts_on,
            a.ends_on           AS ends_on,
            a.bookmark_count    AS bookmark_count,
            a.like_count        AS like_count,
            a.comment_count     AS comment_count,
            a.view_count        AS view_count,
            EXISTS (SELECT 1 FROM bookmarks b
                     WHERE b.article_id = a.id AND b.user_id = :userId)     AS is_bookmarked,
            EXISTS (SELECT 1 FROM article_likes l
                     WHERE l.article_id = a.id AND l.user_id = :userId)     AS is_liked
            """;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // ART-01 / 03 / 04 목록
    // ─────────────────────────────────────────────────────────────────────────

    public Page<ArticleSummaryResponse> search(ArticleSearchCondition condition, Long userId, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        String where = buildWhere(condition, params);

        // ★ 정렬 검증을 count 보다 먼저 합니다.
        //   나중에 하면 결과가 0건일 때 아래에서 조기 반환되어 검증이 통째로 건너뛰어집니다.
        //   같은 잘못된 요청이 데이터 유무에 따라 400 이 되기도 하고 200 이 되기도 합니다.
        String orderBy = ArticleSortSanitizer.toSqlOrderBy(pageable.getSort());

        long total = count(where, params);
        if (total == 0) {
            // 본문 쿼리를 아예 보내지 않습니다. 필터가 촘촘한 화면에서 흔한 경우입니다.
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Query query = em.createNativeQuery(
                "SELECT " + SUMMARY_COLUMNS
                        + " FROM articles a WHERE " + where
                        + " ORDER BY " + orderBy
                        + " LIMIT :limit OFFSET :offset");
        bind(query, params);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        List<ArticleSummaryResponse> content = toSummaries(declareSummaryScalars(query).getResultList());
        return new PageImpl<>(content, pageable, total);
    }

    /** ART-05 인기 공지. 북마크 수 상위. */
    public List<ArticleSummaryResponse> findPopular(Long userId, int limit) {
        Query query = em.createNativeQuery(
                "SELECT " + SUMMARY_COLUMNS
                        + " FROM articles a WHERE " + VISIBLE
                        + " ORDER BY a.bookmark_count DESC, a.id DESC LIMIT :limit");
        query.setParameter("userId", userId);
        query.setParameter("limit", limit);

        return toSummaries(declareSummaryScalars(query).getResultList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ART-02 상세
    // ─────────────────────────────────────────────────────────────────────────

    public Optional<ArticleDetailResponse> findDetail(Long articleId, Long userId) {
        Query query = em.createNativeQuery(
                "SELECT " + SUMMARY_COLUMNS + ", a.content AS content, a.status AS status"
                        + " FROM articles a WHERE a.id = :articleId AND " + VISIBLE_OR_UNDER_REVIEW);
        query.setParameter("articleId", articleId);
        query.setParameter("userId", userId);

        List<Object[]> rows = declareSummaryScalars(query)
                .addScalar("content", StandardBasicTypes.STRING)
                .addScalar("status", StandardBasicTypes.STRING)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.get(0);
        return Optional.of(new ArticleDetailResponse(
                (Long) row[0],
                SourceType.valueOf((String) row[1]),
                (String) row[2],
                (String) row[13],                       // content
                (String) row[3],                        // summary — null 일 수 있습니다
                (OffsetDateTime) row[4],
                (LocalDate) row[5],
                (LocalDate) row[6],
                (Integer) row[7],
                (Integer) row[8],
                (Integer) row[9],
                (Long) row[10],
                (Boolean) row[11],
                (Boolean) row[12],
                "PENDING_REVIEW".equals(row[14]),       // underReview
                findCategories(List.of(articleId)).getOrDefault(articleId, List.of()),
                findSources(articleId),
                findAttachments(articleId)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 부가 정보
    // ─────────────────────────────────────────────────────────────────────────

    private List<ArticleDetailResponse.Source> findSources(Long articleId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT v.id, v.name, av.source_url
                          FROM article_vendors av
                          JOIN vendors v ON v.id = av.vendor_id
                         WHERE av.article_id = :articleId
                         ORDER BY v.name, av.id
                        """)
                .setParameter("articleId", articleId)
                .getResultList();

        List<ArticleDetailResponse.Source> sources = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            sources.add(new ArticleDetailResponse.Source(
                    ((Number) row[0]).longValue(), (String) row[1], (String) row[2]));
        }
        return sources;
    }

    private List<ArticleDetailResponse.Attachment> findAttachments(Long articleId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, file_url, original_name, content_type, size_bytes
                          FROM attachments
                         WHERE article_id = :articleId
                         ORDER BY sort_order, id
                        """)
                .setParameter("articleId", articleId)
                .getResultList();

        List<ArticleDetailResponse.Attachment> attachments = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            attachments.add(new ArticleDetailResponse.Attachment(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    row[4] == null ? null : ((Number) row[4]).longValue()));
        }
        return attachments;
    }

    /** 페이지 전체의 카테고리를 한 번에. 항목마다 부르면 그게 N+1 입니다. */
    private Map<Long, List<NamedRef>> findCategories(List<Long> articleIds) {
        return findRefs(articleIds, """
                SELECT ac.article_id, c.id, c.name
                  FROM article_categories ac
                  JOIN categories c ON c.id = ac.category_id
                 WHERE ac.article_id IN (:articleIds)
                 ORDER BY c.sort_order, c.id
                """);
    }

    private Map<Long, List<NamedRef>> findVendors(List<Long> articleIds) {
        return findRefs(articleIds, """
                SELECT av.article_id, v.id, v.name
                  FROM article_vendors av
                  JOIN vendors v ON v.id = av.vendor_id
                 WHERE av.article_id IN (:articleIds)
                 ORDER BY v.name, v.id
                """);
    }

    private Map<Long, List<NamedRef>> findRefs(List<Long> articleIds, String sql) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("articleIds", articleIds)
                .getResultList();

        Map<Long, List<NamedRef>> byArticle = new HashMap<>();
        for (Object[] row : rows) {
            byArticle.computeIfAbsent(((Number) row[0]).longValue(), key -> new ArrayList<>())
                    .add(new NamedRef(((Number) row[1]).longValue(), (String) row[2]));
        }
        return byArticle;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * WHERE 절을 조립하고 bind parameter 를 채웁니다.
     * count 쿼리와 본문 쿼리가 <b>같은 문자열</b>을 쓰도록 한 곳에서 만듭니다 —
     * 따로 만들면 조건이 어긋나 목록과 총 개수가 맞지 않게 됩니다.
     */
    private String buildWhere(ArticleSearchCondition condition, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(VISIBLE);

        if (condition.sourceType() != null) {
            where.append(" AND a.source_type = :sourceType");
            params.put("sourceType", condition.sourceType().name());
        }
        if (condition.hasCategoryFilter()) {
            where.append(" AND EXISTS (SELECT 1 FROM article_categories ac"
                    + " WHERE ac.article_id = a.id AND ac.category_id IN (:categoryIds))");
            params.put("categoryIds", condition.categoryIds());
        }
        if (condition.hasVendorFilter()) {
            where.append(" AND EXISTS (SELECT 1 FROM article_vendors av"
                    + " WHERE av.article_id = a.id AND av.vendor_id IN (:vendorIds))");
            params.put("vendorIds", condition.vendorIds());
        }
        if (condition.isInterestOnly()) {
            where.append(" AND EXISTS (SELECT 1 FROM article_categories ac"
                    + " JOIN user_interest_categories ui ON ui.category_id = ac.category_id"
                    + " WHERE ac.article_id = a.id AND ui.user_id = :userId)");
        }
        if (condition.hasKeyword()) {
            // search_text 는 이미 소문자로 저장됩니다(inform_normalize_search).
            // likequery() 가 특수문자를 이스케이프하고 %...% 로 감싸 줍니다 — pg_bigm 제공 함수입니다.
            where.append(" AND a.search_text LIKE likequery(lower(:keyword))");
            params.put("keyword", condition.trimmedKeyword());
        }
        if (condition.isDeadlineOnly()) {
            where.append(" AND a.ends_on IS NOT NULL");
        }
        appendPeriodOverlap(condition, where, params);

        return where.toString();
    }

    /**
     * 기간 겹침 필터.
     *
     * <p>한쪽만 주면 반대쪽은 무한으로 봅니다. 공지 쪽 날짜가 비어 있는 것도 무한으로 봅니다 —
     * "마감일 없는 상시 모집"이 기간 검색에서 사라지면 안 됩니다.
     *
     * <p>다만 <b>날짜가 하나도 없는 공지는 제외</b>합니다. 기간을 지정해 찾는다는 건
     * 행사·모집을 찾는다는 뜻인데, 날짜가 없는 일반 안내까지 걸리면 필터가 의미를 잃습니다.
     */
    private void appendPeriodOverlap(ArticleSearchCondition condition,
                                     StringBuilder where, Map<String, Object> params) {
        if (condition.startsFrom() == null && condition.endsTo() == null) {
            return;
        }
        where.append(" AND (a.starts_on IS NOT NULL OR a.ends_on IS NOT NULL)");

        if (condition.startsFrom() != null) {
            where.append(" AND (a.ends_on IS NULL OR a.ends_on >= :startsFrom)");
            params.put("startsFrom", condition.startsFrom());
        }
        if (condition.endsTo() != null) {
            where.append(" AND (a.starts_on IS NULL OR a.starts_on <= :endsTo)");
            params.put("endsTo", condition.endsTo());
        }
    }

    private long count(String where, Map<String, Object> params) {
        Query query = em.createNativeQuery("SELECT count(*) FROM articles a WHERE " + where);
        bind(query, params);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * 쿼리에 실제로 등장하는 파라미터만 바인딩합니다.
     *
     * <p>count 쿼리와 본문 쿼리는 WHERE 절을 공유하지만 SELECT 절이 다릅니다.
     * {@code :userId} 는 본문 쿼리의 {@code EXISTS} 에만 쓰이므로,
     * "관심 분야만 보기" 가 꺼진 요청에서는 count 쿼리에 등장하지 않습니다.
     * 없는 파라미터를 넣으면 Hibernate 가 그 자리에서 거부합니다.
     *
     * <p>SQL 문자열을 뒤지지 않고 Hibernate 가 파싱한 결과를 씁니다 —
     * 문자열 검색은 주석이나 리터럴 안의 {@code :} 에 속습니다.
     */
    private void bind(Query query, Map<String, Object> params) {
        Set<String> declared = query.getParameters().stream()
                .map(Parameter::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        params.forEach((name, value) -> {
            if (declared.contains(name)) {
                query.setParameter(name, value);
            }
        });
    }

    /**
     * native 결과의 타입을 명시합니다.
     *
     * <p>선언하지 않으면 {@code timestamptz} 가 드라이버에 따라 {@code Timestamp} 로도,
     * {@code OffsetDateTime} 으로도 넘어옵니다. 캐스팅이 런타임에 터지는 걸
     * 컴파일 시점에 알 수 없으므로 여기서 못을 박습니다.
     */
    private NativeQuery<Object[]> declareSummaryScalars(Query query) {
        @SuppressWarnings("unchecked")
        NativeQuery<Object[]> nativeQuery = query.unwrap(NativeQuery.class);
        return nativeQuery
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("source_type", StandardBasicTypes.STRING)
                .addScalar("title", StandardBasicTypes.STRING)
                .addScalar("summary", StandardBasicTypes.STRING)
                .addScalar("published_at", StandardBasicTypes.OFFSET_DATE_TIME)
                .addScalar("starts_on", StandardBasicTypes.LOCAL_DATE)
                .addScalar("ends_on", StandardBasicTypes.LOCAL_DATE)
                .addScalar("bookmark_count", StandardBasicTypes.INTEGER)
                .addScalar("like_count", StandardBasicTypes.INTEGER)
                .addScalar("comment_count", StandardBasicTypes.INTEGER)
                .addScalar("view_count", StandardBasicTypes.LONG)
                .addScalar("is_bookmarked", StandardBasicTypes.BOOLEAN)
                .addScalar("is_liked", StandardBasicTypes.BOOLEAN);
    }

    private List<ArticleSummaryResponse> toSummaries(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(row -> (Long) row[0]).toList();
        Map<Long, List<NamedRef>> vendors = findVendors(ids);
        Map<Long, List<NamedRef>> categories = findCategories(ids);

        List<ArticleSummaryResponse> summaries = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            summaries.add(new ArticleSummaryResponse(
                    id,
                    SourceType.valueOf((String) row[1]),
                    (String) row[2],
                    (String) row[3],
                    (OffsetDateTime) row[4],
                    (LocalDate) row[5],
                    (LocalDate) row[6],
                    (Integer) row[7],
                    (Integer) row[8],
                    (Integer) row[9],
                    (Long) row[10],
                    (Boolean) row[11],
                    (Boolean) row[12],
                    vendors.getOrDefault(id, Collections.emptyList()),
                    categories.getOrDefault(id, Collections.emptyList())));
        }
        return summaries;
    }
}
