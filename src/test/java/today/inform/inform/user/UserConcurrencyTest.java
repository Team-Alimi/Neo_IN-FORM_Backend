package today.inform.inform.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.exception.SqlStateErrorMapper;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;
import today.inform.inform.user.repository.PreferenceType;
import today.inform.inform.user.repository.UserRepository;
import today.inform.inform.user.service.UserService;
import today.inform.inform.support.IntegrationTest;

/**
 * {@code users} 행을 <b>서로 다른 사람이</b> 동시에 고칠 때의 규칙.
 *
 * <p>이 테이블만 그렇습니다 — 본인이 설정·탈퇴를 바꾸는 동안 관리자가 권한을 바꿉니다(ADM-16).
 * 낙관적 잠금이 없으면 나중에 커밋한 쪽이 로드 시점 스냅샷으로 행 전체를 다시 써서
 * 상대의 변경을 되돌리는데, <b>갱신 행 수가 1이라 예외도 나지 않습니다.</b>
 *
 * <p>가장 나쁜 결과는 감사 로그입니다. 되돌림 UPDATE 도 {@code role} 을 바꾸는 것이므로
 * 트리거가 반응해 "대상자 본인이 스스로 강등했다" 는 <b>없는 사실</b>을 한 줄 남깁니다.
 * ADM-16 의 존재 이유가 그 기록이라 특히 치명적입니다.
 */
@Transactional
class UserConcurrencyTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // 낙관적 잠금
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 관리자가 권한을 바꾼 뒤 들어오는 낡은 저장은 거부된다 — 통과하면 권한이 조용히 되돌아간다")
    void staleWriteCannotUndoRoleChange() {
        User user = userRepository.saveAndFlush(User.create("concurrency-a@inha.ac.kr", "동시성"));
        Long userId = user.getId();

        // 관리자가 PATCH /admin/users/{id}/role 로 승격하고 커밋한 상황을 흉내냅니다.
        em.createNativeQuery(
                        "UPDATE users SET role = 'ADMIN', version = version + 1 WHERE id = :id")
                .setParameter("id", userId).executeUpdate();

        // 그 사이 열려 있던 사용자 쪽 트랜잭션이 이제야 커밋합니다.
        // version 이 없으면 여기서 role 이 'USER' 로 되돌아가고 아무 오류도 나지 않습니다.
        user.withdraw();

        assertThatThrownBy(() -> em.flush())
                .as("갱신 행 수가 1이라 예외가 없으면 되돌림을 알아챌 단서가 하나도 없습니다")
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("expected row count 1 but was 0");
    }

    @Test
    @DisplayName("★ 권한 변경은 같은 트랜잭션의 다른 컬럼을 되돌리지 않는다 (@DynamicUpdate)")
    void roleChangeDoesNotResurrectAWithdrawnAccount() {
        User user = userRepository.saveAndFlush(User.create("concurrency-b@inha.ac.kr", "동시성"));
        Long userId = user.getId();

        // 사용자가 탈퇴하고 커밋한 상황.
        em.createNativeQuery("""
                        UPDATE users SET status = 'WITHDRAWN', withdrawn_at = now(),
                                         version = version + 1
                         WHERE id = :id
                        """)
                .setParameter("id", userId).executeUpdate();
        em.clear();

        // 관리자가 그 뒤에 권한만 바꿉니다. 이번에는 최신 행을 읽으므로 정상 저장돼야 합니다.
        userRepository.findById(userId).orElseThrow().changeRole(UserRole.USER);
        em.flush();
        em.clear();

        assertThat(statusOf(userId))
                .as("전 컬럼 UPDATE 라면 status='ACTIVE', withdrawn_at=NULL 이 되돌아가 계정이 되살아납니다. "
                        + "ck_users_withdrawn 은 두 값이 짝이 맞으므로 이것을 잡지 못합니다")
                .isEqualTo("WITHDRAWN");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 마지막 관리자 잠금
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 관리자는 스스로 탈퇴할 수 없다 — 막지 않으면 활성 관리자가 0명이 될 수 있다")
    void adminCannotWithdraw() {
        User admin = userRepository.saveAndFlush(User.create("withdraw-admin@inha.ac.kr", "관리자"));
        admin.changeRole(UserRole.ADMIN);
        em.flush();

        assertThatThrownBy(() -> userService.withdraw(admin.getId()))
                .as("ADM-16 의 '자기 자신 금지' 가 탈퇴 경로로 그대로 우회됩니다")
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("다른 관리자");

        em.clear();
        assertThat(statusOf(admin.getId())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("일반 회원 탈퇴는 그대로 된다")
    void normalUserCanStillWithdraw() {
        User user = userRepository.saveAndFlush(User.create("withdraw-user@inha.ac.kr", "일반"));
        em.flush();

        assertThatCode(() -> userService.withdraw(user.getId())).doesNotThrowAnyException();
        em.flush();
        em.clear();

        assertThat(statusOf(user.getId())).isEqualTo("WITHDRAWN");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 비활성 분류
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 비활성 분류는 새로 선택할 수 없다 — 막지 않으면 비활성화가 아무 효과도 없다")
    void inactiveCategoryCannotBeNewlySelected() {
        User user = userRepository.saveAndFlush(User.create("interest-a@inha.ac.kr", "관심"));
        user.completeOnboarding();          // 최소 1개 검증을 피합니다
        em.flush();

        Long categoryId = firstCategoryId();
        deactivate(categoryId);

        Throwable thrown = catchThrowable(() -> {
            userService.updatePreferences(user.getId(), PreferenceType.CATEGORY, Set.of(categoryId));
            em.flush();
        });

        assertThat(thrown)
                .as("club_types 는 IN008 로 막는데 categories 만 빠져 있었습니다")
                .isNotNull();
        assertThat(SqlStateErrorMapper.resolve(thrown))
                .as("IN010 이 400 INACTIVE_CATEGORY 로 나가야 합니다. 매핑이 없으면 500 입니다")
                .isEqualTo(ErrorCode.INACTIVE_CATEGORY);
    }

    @Test
    @DisplayName("★ 이미 선택해 둔 관심분야는 비활성화 뒤에도 그대로 남는다")
    void existingSelectionSurvivesDeactivation() {
        User user = userRepository.saveAndFlush(User.create("interest-b@inha.ac.kr", "관심"));
        user.completeOnboarding();
        em.flush();

        Long categoryId = firstCategoryId();
        userService.updatePreferences(user.getId(), PreferenceType.CATEGORY, Set.of(categoryId));
        em.flush();

        deactivate(categoryId);

        // 저장이 delta 방식이라 기존 선택은 INSERT 를 다시 거치지 않습니다.
        // 전체 삭제 후 재삽입이면 여기서 트리거에 걸려 저장 전체가 실패합니다.
        assertThatCode(() -> {
            userService.updatePreferences(user.getId(), PreferenceType.CATEGORY, Set.of(categoryId));
            em.flush();
        }).doesNotThrowAnyException();

        assertThat(interestCount(user.getId())).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void deactivate(Long categoryId) {
        em.createNativeQuery("UPDATE categories SET is_active = false WHERE id = :id")
                .setParameter("id", categoryId).executeUpdate();
    }

    private Long firstCategoryId() {
        return ((Number) em.createNativeQuery("SELECT id FROM categories ORDER BY id LIMIT 1")
                .getSingleResult()).longValue();
    }

    private String statusOf(Long userId) {
        return (String) em.createNativeQuery("SELECT status FROM users WHERE id = :id")
                .setParameter("id", userId).getSingleResult();
    }

    private int interestCount(Long userId) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM user_interest_categories WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult()).intValue();
    }
}
