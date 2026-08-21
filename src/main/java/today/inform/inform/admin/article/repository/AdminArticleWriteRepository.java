package today.inform.inform.admin.article.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest.VendorLink;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;

/**
 * 관리자 작성·수정에서 JPA 로 표현할 수 없는 부분만 담습니다.
 *
 * <p>세 가지입니다 — 게시글 번호 수동 지정, 시퀀스 보정, 분류·출처 관계 정리.
 */
@Repository
public class AdminArticleWriteRepository {

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // 게시글 번호 수동 지정
    // ─────────────────────────────────────────────────────────────────────────

    public boolean existsById(Long articleId) {
        return (Boolean) em.createNativeQuery(
                        "SELECT EXISTS (SELECT 1 FROM articles WHERE id = :id)")
                .setParameter("id", articleId)
                .getSingleResult();
    }

    /**
     * 번호를 직접 지정해 넣습니다.
     *
     * <p>JPA 로는 할 수 없습니다 — {@code @GeneratedValue(IDENTITY)} 는 id 를 DB 가 정합니다.
     * 중복이면 PK 제약이 23505 로 거부하고, {@code SqlStateErrorMapper} 가 409 로 옮깁니다.
     * <b>중복 확인 버튼은 동시성을 보장하지 않으므로</b> 최종 판정은 여기입니다.
     */
    public void insertWithId(Long id, SourceType sourceType, ArticleStatus status,
                             String title, String content,
                             LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        em.createNativeQuery("""
                        INSERT INTO articles
                               (id, source_type, status, title, content, starts_on, ends_on, created_by)
                        VALUES (:id, :sourceType, :status, :title, :content, :startsOn, :endsOn, :createdBy)
                        """)
                .setParameter("id", id)
                .setParameter("sourceType", sourceType.name())
                .setParameter("status", status.name())
                .setParameter("title", title)
                .setParameter("content", content)
                .setParameter("startsOn", startsOn)
                .setParameter("endsOn", endsOn)
                .setParameter("createdBy", createdBy)
                .executeUpdate();
    }

    /**
     * ★ 번호를 수동 지정했으면 <b>반드시</b> 시퀀스를 밀어올려야 합니다.
     *
     * <p>빠뜨리면 당장은 아무 일도 없습니다. 나중에 크롤러가 수집을 계속하다가
     * 그 번호에 도달하는 순간 PK 충돌이 나고, 크롤러는 배치 단위로 실패하므로
     * <b>그날 수집이 통째로 날아갑니다.</b> 원인도 한참 뒤에 드러납니다.
     *
     * <p>{@code GREATEST} 로 감싸는 이유 — 관리자가 기존 번호보다 작은 값을 지정했을 때
     * 시퀀스를 되돌리면 안 되기 때문입니다. 되돌리면 이미 쓰인 번호를 다시 발급하게 됩니다.
     */
    public void bumpSequence(Long usedId) {
        // ★ 읽고 나서 쓰는 사이에 다른 트랜잭션이 끼어들면 시퀀스가 <b>뒤로</b> 갈 수 있습니다.
        //   A 가 last_value=10 을 읽고 500 으로 밀려는 사이 B 가 같은 10 을 읽고 20 으로 밀면,
        //   A 가 만든 500 번 공지를 시퀀스가 나중에 다시 발급하게 됩니다.
        //   PostgreSQL 에는 "더 클 때만 setval" 이 없어서 자문 잠금으로 순서를 세웁니다.
        //   트랜잭션이 끝나면 자동으로 풀립니다.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('articles_id_seq'))")
                .getSingleResult();

        em.createNativeQuery("""
                        SELECT setval('articles_id_seq',
                                      GREATEST(:usedId, (SELECT last_value FROM articles_id_seq)))
                        """)
                .setParameter("usedId", usedId)
                .getSingleResult();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 분류
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 카테고리를 최종 목록으로 맞춥니다.
     *
     * <p>{@code article_categories} 는 관계뿐이라 통째로 지우고 다시 넣어도 잃을 정보가 없습니다.
     * 출처와 다른 점이 이것입니다.
     */
    public void replaceCategories(Long articleId, List<Long> categoryIds) {
        em.createNativeQuery("DELETE FROM article_categories WHERE article_id = :articleId")
                .setParameter("articleId", articleId)
                .executeUpdate();

        for (Long categoryId : categoryIds.stream().distinct().toList()) {
            em.createNativeQuery("""
                            INSERT INTO article_categories (article_id, category_id)
                            VALUES (:articleId, :categoryId) ON CONFLICT DO NOTHING
                            """)
                    .setParameter("articleId", articleId)
                    .setParameter("categoryId", categoryId)
                    .executeUpdate();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 출처
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 출처를 최종 목록으로 맞춥니다. <b>크롤러가 수집한 행은 건드리지 않습니다.</b>
     *
     * <h2>왜 통째로 교체하면 안 되는가</h2>
     * {@code article_vendors} 한 행은 "원본 게시물 하나" 입니다.
     * 크롤러가 넣은 행에는 {@code external_key}(원본 게시판 글 번호)가 들어 있고,
     * 크롤러는 그 값으로 "이미 수집한 글인가" 를 판단합니다.
     *
     * <p>관리자가 카테고리를 하나 고치려고 저장했을 뿐인데 그 행이 지워지면,
     * 다음 수집에서 크롤러는 그 글을 <b>처음 보는 글로 인식해 공지를 새로 만듭니다.</b>
     * 저장 버튼 한 번에 중복이 계속 생기는 셈입니다.
     *
     * <h2>왜 id 로 대조하는가</h2>
     * 지우고 다시 넣는 방식이면, 화면이 상세 응답을 그대로 되돌려 보냈을 때
     * 수집분이 <b>수기 행으로 한 벌 더 복제</b>됩니다.
     * {@code (article_id, vendor_id)} 유니크가 <b>의도적으로</b> 없어서
     * (같은 게시판 재게시를 모두 보존해야 합니다) 아무것도 막아 주지 않고,
     * 저장할 때마다 한 줄씩 늘어납니다.
     *
     * <p>그래서 행 번호로 대조합니다.
     * <ul>
     *   <li>요청에 id 가 있고 그 행이 수집분이면 — 그대로 둡니다</li>
     *   <li>요청에 id 가 있고 수기 행이면 — URL 만 갱신합니다</li>
     *   <li>요청에 없는 수기 행 — 지웁니다</li>
     *   <li>id 없는 항목 — 새로 넣습니다</li>
     * </ul>
     */
    public void syncVendors(Long articleId, List<VendorLink> vendors) {
        Set<Long> keepIds = vendors.stream()
                .map(VendorLink::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        deleteRemovedManualVendors(articleId, keepIds);

        for (VendorLink vendor : vendors) {
            if (vendor.id() == null) {
                insertVendor(articleId, vendor);
            } else {
                // 수집분이면 아무 행도 갱신되지 않습니다. WHERE 에 external_key IS NULL 이 있어서
                // 화면이 수집분을 되돌려 보내도 원본 식별자가 지워지지 않습니다.
                updateManualVendor(articleId, vendor);
            }
        }
    }

    /**
     * 첨부를 요청 내용과 일치시킵니다. <b>전체 교체</b>입니다.
     *
     * <p>{@code id} 가 있는 항목은 남기고, 없는 항목은 새로 넣고,
     * 요청에 없는 기존 행은 지웁니다 — 출처와 같은 방식입니다.
     *
     * <p><b>DB 에서 지워도 S3 객체는 남습니다.</b> 이 메서드는 그것까지 책임지지 않습니다 —
     * 트랜잭션이 롤백되면 되살릴 수 없는데 객체는 이미 사라진 뒤가 되기 때문입니다.
     * 떨어져 나온 객체는 FIL-02 나 정리 배치가 다룹니다.
     *
     * @param attachments 이미 저장 형식으로 해석된 값. {@code storageType} 과 {@code objectKey} 의
     *                    짝이 맞아야 합니다 — {@code ck_attachments_object_key} 가 강제합니다
     */
    public void syncAttachments(Long articleId, List<ResolvedAttachment> attachments) {
        Set<Long> keepIds = attachments.stream()
                .map(ResolvedAttachment::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        deleteRemovedAttachments(articleId, keepIds);

        int sortOrder = 0;
        for (ResolvedAttachment attachment : attachments) {
            if (attachment.id() == null) {
                insertAttachment(articleId, attachment, sortOrder);
            } else {
                // 기존 행은 순서만 맞춥니다. 파일 자체는 바뀔 수 없습니다 —
                // 다른 파일이면 새 행이고, 그건 id 가 없는 항목으로 들어옵니다.
                em.createNativeQuery(
                                "UPDATE attachments SET sort_order = :sortOrder"
                                        + " WHERE id = :id AND article_id = :articleId")
                        .setParameter("sortOrder", sortOrder)
                        .setParameter("id", attachment.id())
                        .setParameter("articleId", articleId)
                        .executeUpdate();
            }
            sortOrder++;
        }
    }

    private void deleteRemovedAttachments(Long articleId, Set<Long> keepIds) {
        if (keepIds.isEmpty()) {
            em.createNativeQuery("DELETE FROM attachments WHERE article_id = :articleId")
                    .setParameter("articleId", articleId)
                    .executeUpdate();
            return;
        }
        em.createNativeQuery(
                        "DELETE FROM attachments WHERE article_id = :articleId AND id NOT IN (:keepIds)")
                .setParameter("articleId", articleId)
                .setParameter("keepIds", keepIds)
                .executeUpdate();
    }

    private void insertAttachment(Long articleId, ResolvedAttachment attachment, int sortOrder) {
        em.createNativeQuery("""
                        INSERT INTO attachments
                               (article_id, file_url, storage_type, object_key,
                                original_name, content_type, size_bytes, sort_order)
                        VALUES (:articleId, :fileUrl, :storageType, :objectKey,
                                :originalName, :contentType, :sizeBytes, :sortOrder)
                        """)
                .setParameter("articleId", articleId)
                .setParameter("fileUrl", attachment.fileUrl())
                .setParameter("storageType", attachment.storageType())
                .setParameter("objectKey", attachment.objectKey())
                .setParameter("originalName", attachment.originalName())
                .setParameter("contentType", attachment.contentType())
                .setParameter("sizeBytes", attachment.sizeBytes())
                .setParameter("sortOrder", sortOrder)
                .executeUpdate();
    }

    /**
     * 저장 직전 형태의 첨부.
     *
     * <p>요청의 {@code file_url} 이 우리 스토리지 것인지 판정한 결과가 담깁니다.
     * 그 판정은 {@code FileStorage} 를 아는 서비스가 하고, 저장소는 받은 대로 씁니다.
     */
    public record ResolvedAttachment(
            Long id,
            String fileUrl,
            String storageType,
            String objectKey,
            String originalName,
            String contentType,
            Long sizeBytes) {
    }

    private void deleteRemovedManualVendors(Long articleId, Set<Long> keepIds) {
        if (keepIds.isEmpty()) {
            em.createNativeQuery("""
                            DELETE FROM article_vendors
                             WHERE article_id = :articleId AND external_key IS NULL
                            """)
                    .setParameter("articleId", articleId)
                    .executeUpdate();
            return;
        }
        em.createNativeQuery("""
                        DELETE FROM article_vendors
                         WHERE article_id = :articleId AND external_key IS NULL
                           AND id NOT IN (:keepIds)
                        """)
                .setParameter("articleId", articleId)
                .setParameter("keepIds", keepIds)
                .executeUpdate();
    }

    private void insertVendor(Long articleId, VendorLink vendor) {
        em.createNativeQuery("""
                        INSERT INTO article_vendors (article_id, vendor_id, source_url)
                        VALUES (:articleId, :vendorId, :sourceUrl)
                        """)
                .setParameter("articleId", articleId)
                .setParameter("vendorId", vendor.vendorId())
                .setParameter("sourceUrl", vendor.sourceUrl())
                .executeUpdate();
    }

    private void updateManualVendor(Long articleId, VendorLink vendor) {
        em.createNativeQuery("""
                        UPDATE article_vendors
                           SET vendor_id = :vendorId, source_url = :sourceUrl
                         WHERE id = :id AND article_id = :articleId AND external_key IS NULL
                        """)
                .setParameter("id", vendor.id())
                .setParameter("articleId", articleId)
                .setParameter("vendorId", vendor.vendorId())
                .setParameter("sourceUrl", vendor.sourceUrl())
                .executeUpdate();
    }
}
