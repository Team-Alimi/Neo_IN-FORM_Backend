package today.inform.inform.admin.article.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * ADM-13 중복 공지 병합에서 딸린 것들을 옮깁니다.
 *
 * <h2>왜 이 클래스가 통째로 위험한가</h2>
 * {@code articles} 를 참조하는 외래 키가 <b>하나(자기 참조)를 빼고 전부</b>
 * {@code ON DELETE CASCADE} 입니다.
 * 즉 흡수되는 공지를 지우는 순간, <b>옮기지 않은 것은 오류 없이 사라집니다.</b>
 *
 * 빠뜨려도 병합은 성공하고 관리자는 아무것도 눈치채지 못합니다.
 * 사용자가 저장해 둔 북마크와 남긴 댓글이 그렇게 없어집니다.
 *
 * <p>예외인 {@code fk_articles_similar} 는 {@code ON DELETE SET NULL} 이라 사라지지는 않지만,
 * 짝이 되는 {@code similarity_score} 가 남아 더 나쁜 상태를 만듭니다
 * ({@link #clearSimilarityPointingAt} 참조).
 *
 * <p>그래서 옮겨야 할 대상을 스키마에서 뽑아 전부 적어 둡니다.
 * <table border="1">
 *   <tr><th>테이블</th><th>충돌 키</th><th>방식</th></tr>
 *   <tr><td>bookmarks</td><td>PK(user_id, article_id)</td><td>2단계</td></tr>
 *   <tr><td>article_categories</td><td>PK(article_id, category_id)</td><td>2단계(합집합)</td></tr>
 *   <tr><td>attachments</td><td>(article_id, md5(file_url))</td><td>2단계</td></tr>
 *   <tr><td>notifications</td><td>(user_id, article_id, type, dedup_key)</td><td>2단계</td></tr>
 *   <tr><td>article_vendors</td><td>(article_id, vendor_id) — 수기분만</td><td>2단계</td></tr>
 *   <tr><td>comments</td><td>없음</td><td>원댓글 → 답글 순</td></tr>
 *   <tr><td>article_status_logs</td><td>없음</td><td>단순 이동</td></tr>
 * </table>
 *
 * <h2>왜 단일 UPDATE 로는 안 되는가</h2>
 * 복합 PK 나 유니크 인덱스가 있는 테이블은 <b>두 공지를 모두 가진 사용자가 한 명만 있어도</b>
 * UPDATE 전체가 제약 위반으로 실패합니다. 옮길 수 있는 것만 옮기고 남은 중복은 버립니다.
 *
 * <p>카운터는 손대지 않습니다 — 이동도 삭제도 트리거가 잡아 양쪽 개수를 맞춰 줍니다.
 */
@Repository
public class ArticleMergeRepository {

    /** {@code article_status_logs.memo varchar(500)} */
    private static final int MAX_MEMO_LENGTH = 500;

    @PersistenceContext
    private EntityManager em;

    /**
     * 북마크·좋아요. 대상에 없는 것만 옮기고 남은 중복은 버립니다.
     *
     * <p>두 공지를 모두 북마크한 사용자는 대상 쪽 북마크가 이미 있으므로 잃는 것이 없습니다.
     */
    public int moveUserReactions(Long targetId, Long sourceId) {
        int bookmarks = moveByUser("bookmarks", targetId, sourceId);
        return bookmarks;
    }

    private int moveByUser(String table, Long targetId, Long sourceId) {
        int moved = execute("""
                UPDATE %s r SET article_id = :targetId
                 WHERE r.article_id = :sourceId
                   AND NOT EXISTS (SELECT 1 FROM %s t
                                    WHERE t.user_id = r.user_id AND t.article_id = :targetId)
                """.formatted(table, table), targetId, sourceId);

        execute("DELETE FROM %s WHERE article_id = :sourceId".formatted(table), targetId, sourceId);
        return moved;
    }

    /** 분류는 합집합입니다. 이미 대상에 있는 카테고리는 버립니다. */
    public int mergeCategories(Long targetId, Long sourceId) {
        int moved = execute("""
                UPDATE article_categories ac SET article_id = :targetId
                 WHERE ac.article_id = :sourceId
                   AND NOT EXISTS (SELECT 1 FROM article_categories t
                                    WHERE t.article_id = :targetId AND t.category_id = ac.category_id)
                """, targetId, sourceId);

        execute("DELETE FROM article_categories WHERE article_id = :sourceId", targetId, sourceId);
        return moved;
    }

    /**
     * 출처. <b>이걸 빠뜨리면 병합이 소용없어집니다.</b>
     *
     * <p>흡수된 공지의 {@code external_key}(원본 게시판 글 번호)가 사라지면
     * 크롤러는 그 원본을 처음 보는 글로 인식해 <b>다음 수집에서 공지를 새로 만듭니다.</b>
     * 관리자가 병합할 때마다 같은 공지가 되살아나는 셈입니다.
     *
     * <p>수집분은 {@code (vendor_id, external_key)} 로 유니크라 대상으로 옮겨도 충돌하지 않습니다.
     * 수기분만 {@code (article_id, vendor_id)} 유니크에 걸리므로 대조가 필요합니다.
     */
    public int moveVendors(Long targetId, Long sourceId) {
        int moved = execute("""
                UPDATE article_vendors av SET article_id = :targetId
                 WHERE av.article_id = :sourceId
                   AND (av.external_key IS NOT NULL
                        OR NOT EXISTS (SELECT 1 FROM article_vendors t
                                        WHERE t.article_id = :targetId
                                          AND t.vendor_id = av.vendor_id
                                          AND t.external_key IS NULL))
                """, targetId, sourceId);

        execute("DELETE FROM article_vendors WHERE article_id = :sourceId", targetId, sourceId);
        return moved;
    }

    /** 첨부. 같은 파일이 이미 붙어 있으면 버립니다. */
    public int moveAttachments(Long targetId, Long sourceId) {
        int moved = execute("""
                UPDATE attachments a SET article_id = :targetId
                 WHERE a.article_id = :sourceId
                   AND NOT EXISTS (SELECT 1 FROM attachments t
                                    WHERE t.article_id = :targetId
                                      AND md5(t.file_url) = md5(a.file_url))
                """, targetId, sourceId);

        execute("DELETE FROM attachments WHERE article_id = :sourceId", targetId, sourceId);
        return moved;
    }

    /**
     * 댓글. <b>원댓글을 먼저 옮겨야 합니다.</b>
     *
     * <p>{@code trg_comments_10_parent_integrity} 는
     * {@code BEFORE INSERT OR UPDATE OF parent_id, article_id} 라 <b>이 이동에도 발동</b>합니다.
     * 답글을 옮길 때 상위 댓글의 공지 번호를 확인하는데, 상위가 아직 흡수된 공지에 남아 있으면
     * "다른 공지의 댓글을 상위로 지정할 수 없습니다"(IN005)로 거부됩니다.
     *
     * <p>한 문장으로 옮기면 행 처리 순서를 보장할 수 없으므로 두 문장으로 나눕니다.
     */
    public int moveComments(Long targetId, Long sourceId) {
        int roots = execute("""
                UPDATE comments SET article_id = :targetId
                 WHERE article_id = :sourceId AND parent_id IS NULL
                """, targetId, sourceId);

        int replies = execute("""
                UPDATE comments SET article_id = :targetId
                 WHERE article_id = :sourceId AND parent_id IS NOT NULL
                """, targetId, sourceId);

        return roots + replies;
    }

    /** 알림. 이미 같은 알림이 대상에 있으면 버립니다. */
    public int moveNotifications(Long targetId, Long sourceId) {
        int moved = execute("""
                UPDATE notifications n SET article_id = :targetId
                 WHERE n.article_id = :sourceId
                   AND NOT EXISTS (SELECT 1 FROM notifications t
                                    WHERE t.article_id = :targetId
                                      AND t.user_id = n.user_id
                                      AND t.type = n.type
                                      AND t.dedup_key = n.dedup_key)
                """, targetId, sourceId);

        execute("DELETE FROM notifications WHERE article_id = :sourceId", targetId, sourceId);
        return moved;
    }

    /** 상태 이력. 유니크가 없어 그대로 옮깁니다 — 흡수된 공지의 검수 과정도 기록으로 남아야 합니다. */
    public void moveStatusLogs(Long targetId, Long sourceId) {
        execute("UPDATE article_status_logs SET article_id = :targetId WHERE article_id = :sourceId",
                targetId, sourceId);
    }

    /**
     * 병합 사실을 이력에 남깁니다.
     *
     * <p>상태가 바뀌지 않아 감사 트리거가 발동하지 않으므로 직접 넣습니다.
     * {@code from_status} 와 {@code to_status} 가 같은 것은 어색하지만,
     * <b>실제로 상태가 안 바뀐 사건</b>이라 그게 사실입니다.
     * 여기에 남기지 않으면 관리자가 나중에 "이 공지에 왜 남의 댓글이 있지" 를 추적할 수 없습니다.
     */
    public void recordMerge(Long targetId, Long sourceId, Long actorId, String memo) {
        em.createNativeQuery("""
                        INSERT INTO article_status_logs
                               (article_id, from_status, to_status, changed_by, memo)
                        SELECT a.id, a.status, a.status, :actorId, :memo
                          FROM articles a WHERE a.id = :targetId
                        """)
                .setParameter("targetId", targetId)
                .setParameter("actorId", actorId)
                .setParameter("memo", mergeMemo(sourceId, memo))
                .executeUpdate();
    }

    /**
     * {@code article_status_logs.memo} 는 {@code varchar(500)} 인데 요청도 500자까지 받습니다.
     * 접두어를 붙이면 그대로 넘쳐 <b>병합 전체가 롤백</b>됩니다 —
     * 앞에서 옮긴 북마크·댓글까지 전부 되돌아가고, 관리자는 사유가 길어서라는 걸 알 수 없습니다.
     *
     * <p><b>접두어를 살리고 사용자 사유 쪽을 자릅니다.</b> 접두어가 잘리면
     * 어느 공지를 흡수했는지가 사라져 추적할 단서가 없어집니다.
     */
    private static String mergeMemo(Long sourceId, String memo) {
        String note = "공지 #" + sourceId + " 병합";
        if (memo == null || memo.isBlank()) {
            return note;
        }
        String joined = note + " — " + memo;
        return joined.length() <= MAX_MEMO_LENGTH ? joined : joined.substring(0, MAX_MEMO_LENGTH);
    }

    /**
     * 흡수된 공지를 가리키던 중복 의심 판정을 지웁니다.
     *
     * <p><b>{@code articles} 자기 참조 FK 만 {@code ON DELETE SET NULL} 입니다.</b>
     * 나머지가 전부 CASCADE 라 이 하나가 예외인데, 그래서 소스를 지우면
     * 소스를 가리키던 공지들의 {@code similar_article_id} 만 NULL 이 되고
     * <b>{@code similarity_score} 는 그대로 남습니다.</b>
     *
     * <p>결과적으로 "88% 유사 — 비교 대상 없음" 인 공지가 생깁니다.
     * 점수가 임계값을 넘으니 확인 필요 목록에는 계속 걸리는데
     * 관리자는 무엇과 비교해야 할지 알 수 없습니다. 병합을 이미 끝냈는데도요.
     *
     * <p>점수와 상대를 <b>짝으로</b> 지웁니다. 두 컬럼 다
     * {@code updated_at} 화이트리스트 밖이라 수정 시각도 감사 로그도 건드리지 않습니다.
     * 엔티티가 아니라 native 로 하는 이유는 {@code @Version} 을 올리지 않기 위해서입니다 —
     * 병합 때문에 관리자의 수정이 낙관적 잠금에 걸리면 안 됩니다.
     */
    public void clearSimilarityPointingAt(Long sourceId) {
        em.createNativeQuery("""
                        UPDATE articles
                           SET similarity_score = NULL, similar_article_id = NULL
                         WHERE similar_article_id = :sourceId
                        """)
                .setParameter("sourceId", sourceId)
                .executeUpdate();
    }

    /** 흡수된 공지를 지웁니다. 남은 것은 CASCADE 로 사라지므로 위에서 다 옮겼어야 합니다. */
    public void deleteArticle(Long articleId) {
        em.createNativeQuery("DELETE FROM articles WHERE id = :articleId")
                .setParameter("articleId", articleId)
                .executeUpdate();
    }

    /** @return 영향받은 행 수. 병합 응답이 "무엇이 몇 건 옮겨졌는지" 를 돌려주는 데 씁니다. */
    private int execute(String sql, Long targetId, Long sourceId) {
        var query = em.createNativeQuery(sql).setParameter("sourceId", sourceId);
        if (sql.contains(":targetId")) {
            query.setParameter("targetId", targetId);
        }
        return query.executeUpdate();
    }
}
