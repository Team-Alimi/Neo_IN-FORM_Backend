package today.inform.inform.admin.vendor.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import today.inform.inform.admin.vendor.dto.response.AdminVendorResponse.ClubTypeRef;

/**
 * 동아리 ↔ 동아리 유형 연결({@code vendor_club_types}).
 *
 * <p><b>엔티티를 두지 않았습니다.</b> 복합 PK 뿐인 순수 연결 테이블이라 JPA 로 감싸 봐야
 * 얻는 것이 없고, 전체 교체(지우고 다시 넣기)가 주된 연산이라 네이티브가 더 짧습니다.
 *
 * <p>무결성은 DB 가 봅니다 — CLUB 이 아닌 제공처에 붙이면 IN009, 비활성 유형을 새로 붙이면
 * IN008 로 거부됩니다. 여기서 그걸 중복 검사하지 않습니다.
 */
@Repository
public class VendorClubTypeRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 여러 제공처의 유형을 한 번에 읽습니다. 목록 화면에서 항목마다 부르면 그게 N+1 입니다.
     *
     * <p><b>비활성 유형도 함께 내보냅니다.</b> 이미 붙어 있는 연결은 유형을 접어도 유지되는데,
     * 그걸 숨기면 관리자가 수정 화면에서 <b>선택이 비어 보이는 동아리</b>를 보게 되고
     * 그대로 저장하면 멀쩡한 연결이 사라집니다.
     */
    public Map<Long, List<ClubTypeRef>> findByVendorIds(Collection<Long> vendorIds) {
        Map<Long, List<ClubTypeRef>> result = new LinkedHashMap<>();
        if (vendorIds == null || vendorIds.isEmpty()) {
            return result;
        }

        List<?> rows = em.createNativeQuery("""
                        SELECT vct.vendor_id, t.id, t.name
                          FROM vendor_club_types vct
                          JOIN club_types t ON t.id = vct.club_type_id
                         WHERE vct.vendor_id IN (:vendorIds)
                         ORDER BY t.sort_order, t.id
                        """)
                .setParameter("vendorIds", vendorIds)
                .getResultList();

        for (Object row : rows) {
            Object[] cells = (Object[]) row;
            Long vendorId = ((Number) cells[0]).longValue();
            result.computeIfAbsent(vendorId, key -> new ArrayList<>())
                    .add(new ClubTypeRef(((Number) cells[1]).longValue(), (String) cells[2]));
        }
        return result;
    }

    /** 한 제공처의 유형. {@link #findByVendorIds} 의 단건 편의 메서드입니다. */
    public List<ClubTypeRef> findByVendorId(Long vendorId) {
        return findByVendorIds(List.of(vendorId)).getOrDefault(vendorId, List.of());
    }

    /**
     * 전체 교체. 보낸 목록이 곧 최종 상태가 됩니다.
     *
     * <p><b>지우고 다시 넣습니다.</b> 차집합을 계산해 바뀐 것만 건드릴 수도 있지만,
     * 유형은 많아야 여덟 개라 얻는 것이 없고 코드만 길어집니다.
     */
    public void replace(Long vendorId, Collection<Long> clubTypeIds) {
        em.createNativeQuery("DELETE FROM vendor_club_types WHERE vendor_id = :vendorId")
                .setParameter("vendorId", vendorId)
                .executeUpdate();

        if (clubTypeIds == null || clubTypeIds.isEmpty()) {
            return;
        }
        for (Long clubTypeId : clubTypeIds) {
            em.createNativeQuery("""
                            INSERT INTO vendor_club_types (vendor_id, club_type_id)
                            VALUES (:vendorId, :clubTypeId)
                            """)
                    .setParameter("vendorId", vendorId)
                    .setParameter("clubTypeId", clubTypeId)
                    .executeUpdate();
        }
    }
}
