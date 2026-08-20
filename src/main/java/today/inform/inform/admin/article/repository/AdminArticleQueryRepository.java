package today.inform.inform.admin.article.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import today.inform.inform.admin.article.dto.request.AdminArticleSearchCondition;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.dto.response.AdminArticleSummary;
import today.inform.inform.admin.article.dto.response.ReviewStats;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.config.ReviewProperties;
import today.inform.inform.article.dto.response.ArticleSummaryResponse.NamedRef;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;

/**
 * 관리자 공지 조회. <b>사용자 조회 저장소와 완전히 분리합니다.</b>
 *
 * <p>합치면 모든 메서드에 "관리자면 노출 조건 빼고" 분기가 생깁니다.
 * 그 분기를 한 곳에서 빠뜨리면 오류가 아니라 <b>미배포 공지가 사용자에게 새어 나가는</b>
 * 조용한 사고가 됩니다. 코드가 조금 겹치더라도 두 목록의 노출 기준이
 * 서로 섞일 수 없게 물리적으로 떼어 놓는 편이 안전합니다.
 */
@Repository
@RequiredArgsConstructor
public class AdminArticleQueryRepository {

    private static final String COLUMNS = """
            a.id                 AS id,
            a.source_type        AS source_type,
            a.status             AS status,
            a.title              AS title,
            a.starts_on          AS starts_on,
            a.ends_on            AS ends_on,
            a.similarity_score   AS similarity_score,
            a.similar_article_id AS similar_article_id,
            a.updated_at         AS updated_at
            """;

    /**
     * ADM-12 "확인 필요" 조건.
     *
     * <p>별도 상태가 아니라 <b>필터</b>입니다. 상태로 만들었다면 검수 대기와 확인 필요가
     * 배타적이 되어, 확인 필요를 처리하는 순간 검수 대기 목록에서 사라졌을 겁니다.
     *
     * <p><b>본문 길이 판정에 {@code search_text} 를 쓰면 안 됩니다.</b>
     * 그 컬럼은 {@code inform_normalize_search(title, content)} 라 <b>제목이 포함</b>돼 있습니다.
     * 제목이 임계값을 미리 갉아먹어서, 제목만 긴 공지는 본문이 완전히 비어 있어도 통과합니다.
     * 학교 공지 제목은 원래 길기 때문에 흔한 경우입니다.
     *
     * <p>그렇다고 {@code a.content} 를 그대로 재면 안 됩니다 — HTML 이라
     * {@code <p><br></p>} 만 있어도 길이가 12 입니다.
     * 같은 정규화 함수를 <b>본문에만</b> 적용해 태그를 걷어낸 길이를 봅니다.
     *
     * <p>행마다 함수를 부르므로 인덱스를 타지 못합니다. 이 목록은 상태로 이미 좁혀져 있어
     * 문제가 되지 않지만, 대상이 커지면 본문 전용 생성 컬럼을 두는 편이 낫습니다.
     */
    private static final String NEEDS_CHECK = """
            (   a.similarity_score >= :similarityThreshold
             OR a.starts_on IS NULL
             OR a.ends_on IS NULL
             OR length(inform_normalize_search('', a.content)) < :minContentLength
             OR NOT EXISTS (SELECT 1 FROM article_categories ac WHERE ac.article_id = a.id)
             OR NOT EXISTS (SELECT 1 FROM article_vendors av
                             WHERE av.article_id = a.id AND av.source_url IS NOT NULL))
            """;

    @PersistenceContext
    private EntityManager em;

    private final ReviewProperties reviewProperties;

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-02 대시보드
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 카드 세 개를 <b>한 번의 스캔</b>으로 셉니다.
     *
     * <p>세 번 나눠 세면 그 사이 크롤러가 새 공지를 넣어 카드끼리 앞뒤가 안 맞을 수 있습니다.
     * {@code FILTER} 절로 한 문장에 담으면 같은 스냅샷에서 나온 숫자가 됩니다.
     */
    public ReviewStats stats() {
        Query query = em.createNativeQuery("""
                SELECT count(*) FILTER (WHERE a.status = 'PENDING_REVIEW')    AS pending_review,
                       count(*) FILTER (WHERE a.status = 'READY_TO_PUBLISH')  AS ready_to_publish,
                       count(*) FILTER (WHERE a.status = 'PENDING_REVIEW' AND """ + NEEDS_CHECK + """
                       )                                                      AS needs_check
                  FROM articles a
                """);
        query.setParameter("similarityThreshold", BigDecimal.valueOf(reviewProperties.similarityThreshold()));
        query.setParameter("minContentLength", reviewProperties.minContentLength());

        Object[] row = (Object[]) query.getSingleResult();
        return new ReviewStats(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-03 / ADM-12 목록
    // ─────────────────────────────────────────────────────────────────────────

    public Page<AdminArticleSummary> search(AdminArticleSearchCondition condition, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = buildWhere(condition, params);

        long total = count(where, params);
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Query query = em.createNativeQuery(
                "SELECT " + COLUMNS
                        + " FROM articles a WHERE " + where
                        // 관리자 목록은 "최근에 손댄 것" 순입니다. 사용자 피드의 발행순과 다릅니다 —
                        // 검수 화면에서 중요한 건 무엇이 방금 바뀌었는가입니다.
                        + " ORDER BY a.updated_at DESC, a.id DESC"
                        + " LIMIT :limit OFFSET :offset");
        bind(query, params);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        return new PageImpl<>(toSummaries(declareScalars(query).getResultList(), Map.of()), pageable, total);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-09 휴지통
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 휴지통 목록. "상태" 컬럼에 <b>휴지통에 들어가기 직전 상태</b>를 채웁니다.
     *
     * <p>{@code articles.status} 는 TRASHED 로 덮여 있어 원래 상태를 알 수 없습니다.
     * 이력에서 가져와야 관리자가 "이게 검수 중이던 건지 배포됐던 건지" 를 알고 복구할 수 있습니다.
     */
    public Page<AdminArticleSummary> searchTrashed(Pageable pageable) {
        long total = count("a.status = 'TRASHED'", Map.of());
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Query query = em.createNativeQuery(
                "SELECT " + COLUMNS
                        + " FROM articles a WHERE a.status = 'TRASHED'"
                        + " ORDER BY a.updated_at DESC, a.id DESC"
                        + " LIMIT :limit OFFSET :offset");
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        List<Object[]> rows = declareScalars(query).getResultList();
        List<Long> ids = rows.stream().map(row -> (Long) row[0]).toList();

        return new PageImpl<>(toSummaries(rows, findPreviousStatuses(ids)), pageable, total);
    }

    /**
     * 휴지통 직전 상태.
     *
     * <p>{@code DISTINCT ON} 으로 공지마다 <b>가장 최근</b> 휴지통 이동 기록만 남깁니다.
     * 휴지통에 넣었다 꺼냈다를 반복한 공지는 이력이 여러 줄이라, 가장 오래된 것을 집으면
     * 엉뚱한 상태로 복구됩니다.
     *
     * <p>{@code created_at} 만으로 정렬하면 안 됩니다 — 일괄 처리는 한 트랜잭션이라
     * {@code now()} 가 고정이고 여러 줄의 시각이 같습니다. {@code id DESC} 가 순서를 정합니다.
     */
    public Map<Long, ArticleStatus> findPreviousStatuses(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT DISTINCT ON (article_id) article_id, from_status
                          FROM article_status_logs
                         WHERE to_status = 'TRASHED' AND article_id IN (:articleIds)
                         ORDER BY article_id, created_at DESC, id DESC
                        """)
                .setParameter("articleIds", articleIds)
                .getResultList();

        Map<Long, ArticleStatus> byArticle = new HashMap<>();
        for (Object[] row : rows) {
            if (row[1] != null) {
                byArticle.put(((Number) row[0]).longValue(), ArticleStatus.valueOf((String) row[1]));
            }
        }
        return byArticle;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-04 상세
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 관리자 상세. <b>상태를 가리지 않습니다</b> — 검수 대기든 휴지통이든 열려야 합니다.
     * 그게 사용자 상세와의 본질적인 차이입니다.
     */
    public Optional<AdminArticleDetail> findDetail(Long articleId) {
        Query query = em.createNativeQuery("""
                        SELECT a.id                 AS id,
                               a.source_type        AS source_type,
                               a.status             AS status,
                               a.title              AS title,
                               a.content            AS content,
                               a.summary            AS summary,
                               a.starts_on          AS starts_on,
                               a.ends_on            AS ends_on,
                               a.published_at       AS published_at,
                               a.similarity_score   AS similarity_score,
                               a.similar_article_id AS similar_article_id,
                               a.created_by         AS created_by,
                               a.created_at         AS created_at,
                               a.updated_at         AS updated_at
                          FROM articles a WHERE a.id = :articleId
                        """)
                .setParameter("articleId", articleId);

        @SuppressWarnings("unchecked")
        NativeQuery<Object[]> nativeQuery = query.unwrap(NativeQuery.class);
        List<Object[]> rows = nativeQuery
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("source_type", StandardBasicTypes.STRING)
                .addScalar("status", StandardBasicTypes.STRING)
                .addScalar("title", StandardBasicTypes.STRING)
                .addScalar("content", StandardBasicTypes.STRING)
                .addScalar("summary", StandardBasicTypes.STRING)
                .addScalar("starts_on", StandardBasicTypes.LOCAL_DATE)
                .addScalar("ends_on", StandardBasicTypes.LOCAL_DATE)
                .addScalar("published_at", StandardBasicTypes.OFFSET_DATE_TIME)
                .addScalar("similarity_score", StandardBasicTypes.BIG_DECIMAL)
                .addScalar("similar_article_id", StandardBasicTypes.LONG)
                .addScalar("created_by", StandardBasicTypes.LONG)
                .addScalar("created_at", StandardBasicTypes.OFFSET_DATE_TIME)
                .addScalar("updated_at", StandardBasicTypes.OFFSET_DATE_TIME)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        return Optional.of(new AdminArticleDetail(
                (Long) row[0],
                SourceType.valueOf((String) row[1]),
                ArticleStatus.valueOf((String) row[2]),
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (LocalDate) row[6],
                (LocalDate) row[7],
                (OffsetDateTime) row[8],
                (BigDecimal) row[9],
                (Long) row[10],
                (Long) row[11],
                (OffsetDateTime) row[12],
                (OffsetDateTime) row[13],
                findDetailCategories(articleId),
                findDetailVendors(articleId)));
    }

    private List<AdminArticleDetail.CategoryRef> findDetailCategories(Long articleId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT c.id, c.name FROM article_categories ac
                          JOIN categories c ON c.id = ac.category_id
                         WHERE ac.article_id = :articleId ORDER BY c.sort_order, c.id
                        """)
                .setParameter("articleId", articleId).getResultList();

        List<AdminArticleDetail.CategoryRef> categories = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            categories.add(new AdminArticleDetail.CategoryRef(
                    ((Number) row[0]).longValue(), (String) row[1]));
        }
        return categories;
    }

    /** 원본 식별자까지 내려줍니다 — 관리자가 수집분과 수기 추가분을 구분해야 합니다. */
    private List<AdminArticleDetail.VendorRef> findDetailVendors(Long articleId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT av.id, v.id, v.name, av.source_url, av.external_key
                          FROM article_vendors av
                          JOIN vendors v ON v.id = av.vendor_id
                         WHERE av.article_id = :articleId ORDER BY av.id
                        """)
                .setParameter("articleId", articleId).getResultList();

        List<AdminArticleDetail.VendorRef> vendors = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            vendors.add(new AdminArticleDetail.VendorRef(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    (String) row[2],
                    (String) row[3],
                    (String) row[4]));
        }
        return vendors;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-11 이력
    // ─────────────────────────────────────────────────────────────────────────

    public List<StatusLogResponse> findStatusLogs(Long articleId) {
        Query query = em.createNativeQuery("""
                        SELECT l.id           AS id,
                               l.from_status  AS from_status,
                               l.to_status    AS to_status,
                               l.changed_by   AS changed_by,
                               u.name         AS changed_by_name,
                               l.memo         AS memo,
                               l.created_at   AS created_at
                          FROM article_status_logs l
                          LEFT JOIN users u ON u.id = l.changed_by
                         WHERE l.article_id = :articleId
                         ORDER BY l.created_at DESC, l.id DESC
                        """)
                .setParameter("articleId", articleId);

        // ★ 타입을 선언하지 않으면 timestamptz 가 무엇으로 올지 드라이버에 달려 있습니다.
        //   캐스팅에 실패해도 예외가 아니라 null 이 되어, 응답에서 시각이 조용히 빠집니다
        //   (non_null 직렬화 설정 때문에 필드 자체가 사라집니다).
        @SuppressWarnings("unchecked")
        NativeQuery<Object[]> nativeQuery = query.unwrap(NativeQuery.class);
        List<Object[]> rows = nativeQuery
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("from_status", StandardBasicTypes.STRING)
                .addScalar("to_status", StandardBasicTypes.STRING)
                .addScalar("changed_by", StandardBasicTypes.LONG)
                .addScalar("changed_by_name", StandardBasicTypes.STRING)
                .addScalar("memo", StandardBasicTypes.STRING)
                .addScalar("created_at", StandardBasicTypes.OFFSET_DATE_TIME)
                .getResultList();

        List<StatusLogResponse> logs = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            logs.add(new StatusLogResponse(
                    (Long) row[0],
                    row[1] == null ? null : ArticleStatus.valueOf((String) row[1]),
                    ArticleStatus.valueOf((String) row[2]),
                    (Long) row[3],
                    (String) row[4],
                    (String) row[5],
                    (OffsetDateTime) row[6]));
        }
        return logs;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildWhere(AdminArticleSearchCondition condition, Map<String, Object> params) {
        StringBuilder where = new StringBuilder("a.status = :status");
        params.put("status", condition.status().name());

        if (condition.articleId() != null) {
            where.append(" AND a.id = :articleId");
            params.put("articleId", condition.articleId());
        }
        if (condition.hasTitle()) {
            // 관리자 검색은 제목만 봅니다. 사용자 검색과 달리 pg_bigm 을 쓰지 않는 이유는
            // 대상이 한 상태로 이미 좁혀져 있고, 관리자는 정확한 제목 조각으로 찾기 때문입니다.
            //
            // ★ 검색어의 %, _ 를 이스케이프합니다. 그대로 넘기면 사용자가 입력한 글자가
            //   패턴 문법으로 해석됩니다 — "100%" 를 찾으면 "100" 으로 시작하는 제목이 전부 걸리고,
            //   "%" 하나면 전체가 걸립니다. 제목에 % 가 들어가는 공지는 드물지 않습니다.
            where.append(" AND a.title ILIKE :title ESCAPE '\\'");
            params.put("title", "%" + escapeLike(condition.title().trim()) + "%");
        }
        if (condition.vendorId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM article_vendors av"
                    + " WHERE av.article_id = a.id AND av.vendor_id = :vendorId)");
            params.put("vendorId", condition.vendorId());
        }
        if (condition.categoryId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM article_categories ac"
                    + " WHERE ac.article_id = a.id AND ac.category_id = :categoryId)");
            params.put("categoryId", condition.categoryId());
        }
        if (condition.startsFrom() != null) {
            where.append(" AND (a.ends_on IS NULL OR a.ends_on >= :startsFrom)");
            params.put("startsFrom", condition.startsFrom());
        }
        if (condition.endsTo() != null) {
            where.append(" AND (a.starts_on IS NULL OR a.starts_on <= :endsTo)");
            params.put("endsTo", condition.endsTo());
        }
        if (condition.isNeedsCheck()) {
            where.append(" AND ").append(NEEDS_CHECK);
            params.put("similarityThreshold", BigDecimal.valueOf(reviewProperties.similarityThreshold()));
            params.put("minContentLength", reviewProperties.minContentLength());
        }
        return where.toString();
    }

    /** LIKE 패턴 문법으로 해석되는 글자를 막습니다. 역슬래시 자신도 이스케이프해야 합니다. */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private long count(String where, Map<String, Object> params) {
        Query query = em.createNativeQuery("SELECT count(*) FROM articles a WHERE " + where);
        bind(query, params);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** 쿼리에 실제로 등장하는 파라미터만 바인딩합니다. count 와 본문은 SELECT 절이 달라 목록이 다릅니다. */
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

    private NativeQuery<Object[]> declareScalars(Query query) {
        @SuppressWarnings("unchecked")
        NativeQuery<Object[]> nativeQuery = query.unwrap(NativeQuery.class);
        return nativeQuery
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("source_type", StandardBasicTypes.STRING)
                .addScalar("status", StandardBasicTypes.STRING)
                .addScalar("title", StandardBasicTypes.STRING)
                .addScalar("starts_on", StandardBasicTypes.LOCAL_DATE)
                .addScalar("ends_on", StandardBasicTypes.LOCAL_DATE)
                .addScalar("similarity_score", StandardBasicTypes.BIG_DECIMAL)
                .addScalar("similar_article_id", StandardBasicTypes.LONG)
                .addScalar("updated_at", StandardBasicTypes.OFFSET_DATE_TIME);
    }

    private List<AdminArticleSummary> toSummaries(List<Object[]> rows,
                                                  Map<Long, ArticleStatus> previousStatuses) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(row -> (Long) row[0]).toList();
        Map<Long, List<NamedRef>> vendors = findRefs(ids, """
                SELECT av.article_id, v.id, v.name
                  FROM article_vendors av JOIN vendors v ON v.id = av.vendor_id
                 WHERE av.article_id IN (:articleIds)
                 ORDER BY v.name, v.id
                """);
        Map<Long, List<NamedRef>> categories = findRefs(ids, """
                SELECT ac.article_id, c.id, c.name
                  FROM article_categories ac JOIN categories c ON c.id = ac.category_id
                 WHERE ac.article_id IN (:articleIds)
                 ORDER BY c.sort_order, c.id
                """);

        List<AdminArticleSummary> summaries = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            summaries.add(new AdminArticleSummary(
                    id,
                    SourceType.valueOf((String) row[1]),
                    ArticleStatus.valueOf((String) row[2]),
                    (String) row[3],
                    (LocalDate) row[4],
                    (LocalDate) row[5],
                    (BigDecimal) row[6],
                    (Long) row[7],
                    (OffsetDateTime) row[8],
                    previousStatuses.get(id),
                    // 화면은 출처를 최대 3개까지만 보여 줍니다. 자르는 건 클라이언트 몫이라
                    // 서버는 전부 내려보냅니다 — 3개로 잘라 보내면 "더 있는지" 를 알 수 없습니다.
                    vendors.getOrDefault(id, List.of()),
                    categories.getOrDefault(id, List.of())));
        }
        return summaries;
    }

    private Map<Long, List<NamedRef>> findRefs(List<Long> articleIds, String sql) {
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
}
