package today.inform.inform.vendor.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.vendor.entity.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    boolean existsByInitial(String initial);

    /**
     * 관리자 제공처 목록.
     *
     * <p><b>정렬을 쿼리에 고정하고 {@code Pageable} 을 받지 않습니다.</b>
     * Spring Data 는 클라이언트가 보낸 {@code sort} 를 검증 없이 ORDER BY 뒤에 이어 붙이므로,
     * {@code ?sort=foo} 하나면 JPQL 파싱 단계에서 터지고 SQLSTATE 가 없어 500 이 나갑니다.
     * 제공처는 많아야 수백 건이라 페이징 자체가 필요 없습니다.
     *
     * <p><b>비활성 제공처도 포함합니다.</b> 관리 화면은 다시 켤 대상을 볼 수 있어야 합니다.
     * 사용자에게 나가는 목록(COM-01)은 이것과 별개이며 활성만 내보내야 합니다.
     */
    @Query("""
            SELECT v FROM Vendor v
             WHERE (:type IS NULL OR v.type = :type)
               AND (:active IS NULL OR v.active = :active)
             ORDER BY v.type ASC, v.name ASC, v.id ASC
            """)
    List<Vendor> findForAdmin(@Param("type") SourceType type, @Param("active") Boolean active);
}
