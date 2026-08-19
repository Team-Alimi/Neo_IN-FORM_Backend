package today.inform.inform.user.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 개인화 junction 3종의 조회·저장.
 *
 * <p><b>JPA 엔티티를 두지 않는 이유</b>
 * 복합 PK 엔티티는 Spring Data {@code save()} 가 {@code isNew()} 를 false 로 판정해
 * {@code merge()} 를 호출합니다. INSERT 전에 SELECT 가 한 번 더 나가고
 * {@code ON CONFLICT} 를 쓸 수 없습니다. 이 테이블들은 관계 그 자체가 전부라
 * 엔티티로 얻을 게 없으므로 native 로 다룹니다.
 *
 * <p><b>replace-all 을 쓰지 않는 이유</b>
 * "전체 삭제 후 전체 재삽입" 으로 구현하면, 이미 선택되어 있던 비활성 동아리 유형을
 * 다시 넣는 순간 DB trigger 가 막아 <b>저장 전체가 실패</b>합니다.
 * 사용자는 유형 하나를 추가하려다 오류를 받습니다.
 * 그래서 {@link #replace} 는 delta 만 반영합니다.
 */
@Repository
public class UserPreferenceRepository {

    @PersistenceContext
    private EntityManager em;

    /** 사용자가 선택한 대상 id 목록. */
    @SuppressWarnings("unchecked")
    public Set<Long> findIds(Long userId, PreferenceType type) {
        List<Number> rows = em.createNativeQuery(
                        "SELECT " + type.column() + " FROM " + type.table() + " WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getResultList();
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(n -> ids.add(n.longValue()));
        return ids;
    }

    /**
     * 선택 항목과 표시명을 함께 조회합니다.
     * 클라이언트가 마스터 목록을 다시 부르지 않아도 화면을 그릴 수 있습니다.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findSelectedWithName(Long userId, PreferenceType type) {
        return em.createNativeQuery(
                        "SELECT m.id, m.name FROM " + type.table() + " j "
                                + " JOIN " + type.masterTable() + " m ON m.id = j." + type.column()
                                + " WHERE j.user_id = :userId"
                                + " ORDER BY m.name")
                .setParameter("userId", userId)
                .getResultList();
    }

    /**
     * 최종 목록을 받아 delta 만 반영합니다.
     *
     * @return 실제로 추가된 개수 (없으면 0)
     */
    public int replace(Long userId, PreferenceType type, Set<Long> targetIds) {
        Set<Long> current = findIds(userId, type);

        Set<Long> toAdd = new LinkedHashSet<>(targetIds);
        toAdd.removeAll(current);

        Set<Long> toRemove = new LinkedHashSet<>(current);
        toRemove.removeAll(targetIds);

        if (!toRemove.isEmpty()) {
            em.createNativeQuery(
                            "DELETE FROM " + type.table()
                                    + " WHERE user_id = :userId AND " + type.column() + " IN (:ids)")
                    .setParameter("userId", userId)
                    .setParameter("ids", toRemove)
                    .executeUpdate();
        }

        // 온보딩 선택 개수가 많아야 십수 개라 건별 INSERT 로 충분합니다.
        // ON CONFLICT DO NOTHING 은 동시 요청이 겹쳐도 안전하게 만듭니다.
        for (Long id : toAdd) {
            em.createNativeQuery(
                            "INSERT INTO " + type.table() + " (user_id, " + type.column() + ") "
                                    + "VALUES (:userId, :targetId) ON CONFLICT DO NOTHING")
                    .setParameter("userId", userId)
                    .setParameter("targetId", id)
                    .executeUpdate();
        }

        return toAdd.size();
    }

    /**
     * 탈퇴 시 개인화 관계를 모두 지웁니다.
     * soft delete 라 FK CASCADE 가 발동하지 않으므로 명시적으로 처리해야 합니다.
     */
    public void deleteAll(Long userId, PreferenceType type) {
        em.createNativeQuery("DELETE FROM " + type.table() + " WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
