package today.inform.inform.admin.file.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 첨부 조회. FIL-02 와 ADM-10(영구 삭제 시 S3 정리)이 씁니다. */
@Repository
public class AttachmentQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 이미 공지에 연결된 주소만 골라 냅니다.
     *
     * <p>대상이 최대 100건이라 {@code IN} 으로 충분합니다.
     * {@code uk_attachments_article_file} 은 공지 단위 유니크라 여기서는 쓸 수 없습니다.
     */
    @Transactional(readOnly = true)
    public List<String> findLinkedUrls(List<String> fileUrls) {
        if (fileUrls.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery(
                        "SELECT DISTINCT file_url FROM attachments WHERE file_url IN (:urls)")
                .setParameter("urls", fileUrls)
                .getResultList();
        return rows;
    }

    /**
     * 공지들에 붙은 S3 객체 키.
     *
     * <p><b>공지를 지우기 전에 읽어야 합니다.</b> {@code attachments} 가
     * {@code ON DELETE CASCADE} 라 공지를 지우는 순간 이 행들도 함께 사라지고,
     * 그러면 어떤 객체를 지워야 하는지 알 방법이 없어집니다.
     * v1 이 정확히 이 지점에서 실패해 스토리지에 고아 객체가 쌓였습니다.
     *
     * <p>{@code EXTERNAL} 은 제외합니다 — 원본 사이트의 파일이라 우리 것이 아닙니다.
     */
    @Transactional(readOnly = true)
    public List<String> findS3ObjectKeys(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery("""
                        SELECT object_key FROM attachments
                         WHERE article_id IN (:articleIds)
                           AND storage_type = 'S3'
                           AND object_key IS NOT NULL
                        """)
                .setParameter("articleIds", articleIds)
                .getResultList();
        return rows;
    }
}
