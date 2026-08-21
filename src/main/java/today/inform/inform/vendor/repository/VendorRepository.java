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
     * 제공처 목록. 관리자({@code /admin/vendors})와 사용자({@code /vendors})가 함께 씁니다.
     *
     * <p><b>노출 기준은 호출부가 정합니다.</b> 사용자용 서비스가 {@code active} 를 {@code true} 로
     * 못 박고, 관리자용만 {@code null}(전체)을 넘깁니다.
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
    List<Vendor> search(@Param("type") SourceType type, @Param("active") Boolean active);

    /**
     * COM-01 사용자용 목록. <b>이름 오름차순</b>입니다(명세 4.7).
     *
     * <p><b>{@link #search} 를 재사용하면 안 됩니다.</b> 그쪽은 관리 화면용이라
     * {@code type} 으로 먼저 묶는데, {@code type} 은 문자열로 저장되므로
     * {@code 'CLUB' < 'SCHOOL'} 이 되어 <b>동아리 전체가 학과 전체보다 앞에</b> 옵니다.
     * 이름 오름차순은 각 묶음 안에서만 성립하고, 오류 없이 순서만 달라지므로
     * 화면을 눈으로 보기 전에는 드러나지 않습니다.
     *
     * <p>정렬 하나 때문에 쿼리를 나누는 것이 과해 보이지만, 두 목록은 <b>보는 사람이 다릅니다.</b>
     * 한쪽 요구가 바뀔 때 다른 쪽이 조용히 따라 바뀌지 않도록 떼어 둡니다.
     */
    @Query("""
            SELECT v FROM Vendor v
             WHERE v.active = true
               AND (:type IS NULL OR v.type = :type)
             ORDER BY v.name ASC, v.id ASC
            """)
    List<Vendor> findActiveOrderByName(@Param("type") SourceType type);
}
