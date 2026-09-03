package today.inform.inform.clubtype.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import today.inform.inform.clubtype.entity.ClubType;

public interface ClubTypeRepository extends JpaRepository<ClubType, Long> {

    /**
     * 활성 유형만, 화면 순서대로.
     *
     * <p>{@code sortOrder} 만으로 정렬하면 같은 값일 때 순서가 매 호출마다 달라집니다.
     * 온보딩 화면의 칩 위치가 새로고침마다 바뀌므로 {@code name} · {@code id} 로 고정합니다.
     * ({@code CategoryRepository.search} 와 같은 이유입니다)
     */
    @Query("""
            SELECT t FROM ClubType t
             WHERE t.active = true
             ORDER BY t.sortOrder ASC, t.name ASC, t.id ASC
            """)
    List<ClubType> findActive();
}
