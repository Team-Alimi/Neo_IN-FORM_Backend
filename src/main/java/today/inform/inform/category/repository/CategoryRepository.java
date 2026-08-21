package today.inform.inform.category.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCode(String code);

    Optional<Category> findByName(String name);

    /** 분류 목록. 관리자와 사용자가 함께 씁니다. active 가 null 이면 비활성 포함 — 관리 화면 전용입니다. (정렬을 고정하고 Pageable 을 안 받는 이유는 {@code VendorRepository} 참조) */
    @Query("""
            SELECT c FROM Category c
             WHERE (:active IS NULL OR c.active = :active)
             ORDER BY c.sortOrder ASC, c.name ASC, c.id ASC
            """)
    List<Category> search(@Param("active") Boolean active);

    /**
     * 삭제 판정 전에 분류 행을 잠급니다.
     *
     * <p><b>{@code FOR UPDATE} 여야 합니다.</b> 자식 INSERT
     * ({@code article_categories} · {@code user_interest_categories})는 참조하는 부모 행에
     * {@code FOR KEY SHARE} 를 잡는데, {@code FOR UPDATE} 만 그것과 충돌합니다.
     * {@code @Lock(PESSIMISTIC_WRITE)} 는 Hibernate 6+ 에서 {@code FOR NO KEY UPDATE} 로 나가고
     * 그건 {@code FOR KEY SHARE} 를 막지 못해, "쓰는 곳 없음" 으로 판정한 <b>직후</b>
     * 공지가 이 분류를 달 수 있습니다.
     * (같은 함정을 {@code CommentRepository.lockById} 에서 이미 밟았습니다)
     *
     * <p>잠그지 않으면 판정과 DELETE 사이에서 23503 이 나는데, 그 SQLSTATE 는
     * {@code RELATED_RESOURCE_NOT_FOUND}("참조 대상이 존재하지 않습니다", 400)로 매핑돼 있습니다.
     * 실제 원인은 정반대 — <b>참조하는 쪽이 남아 있어서</b> 못 지운 것입니다.
     * 관리자는 "없는 분류를 지우려 했다" 는 뜻의 메시지를 받고 원인을 찾지 못합니다.
     */
    @Query(value = "SELECT id FROM categories WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("id") Long id);

    /**
     * 이 분류를 쓰는 곳이 있는지. 공지 분류와 사용자 관심분야 <b>둘 다</b> 봅니다.
     *
     * <p>둘 다 {@code ON DELETE RESTRICT} 라, 한쪽만 확인하면 남은 한쪽에서 DELETE 가 실패합니다.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM article_categories       WHERE category_id = :id)
                OR EXISTS (SELECT 1 FROM user_interest_categories WHERE category_id = :id)
            """, nativeQuery = true)
    boolean isInUse(@Param("id") Long id);

    /**
     * 목록에서 "지울 수 있는 분류" 를 가려내기 위한 것.
     *
     * <p>행마다 {@link #isInUse} 를 부르면 분류 수만큼 쿼리가 나갑니다.
     * {@code EXISTS} 는 첫 행에서 멈추므로 한 문장으로 묶어도 비쌉니다.
     *
     * <p>이 값이 없으면 관리 화면은 삭제 버튼을 눌러 봐야 되는지 알 수 있습니다.
     * 실제로 지워지는 분류는 거의 없으므로(운영에 들어간 분류는 전부 쓰이는 중),
     * 눌러 보고 409 를 받는 흐름은 관리자에게 대부분 헛수고입니다.
     */
    @Query(value = """
            SELECT c.id
              FROM categories c
             WHERE EXISTS (SELECT 1 FROM article_categories       ac WHERE ac.category_id = c.id)
                OR EXISTS (SELECT 1 FROM user_interest_categories ui WHERE ui.category_id = c.id)
            """, nativeQuery = true)
    List<Long> findInUseIds();
}
