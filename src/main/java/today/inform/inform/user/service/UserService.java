package today.inform.inform.user.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.user.dto.response.SelectedItemResponse;
import today.inform.inform.user.dto.response.UserProfileResponse;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;
import today.inform.inform.user.repository.PreferenceType;
import today.inform.inform.user.repository.UserPreferenceRepository;
import today.inform.inform.user.repository.UserRepository;

/**
 * USER-01 ~ USER-07.
 *
 * <p>온보딩과 마이페이지 수정이 <b>같은 메서드</b>를 씁니다.
 * 두 벌로 만들면 검증 규칙이 갈리고 한쪽만 고치는 사고가 납니다.
 * 차이는 "최소 개수 검증을 하느냐" 뿐이라 파라미터로 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(findActive(userId));
    }

    /** USER-04. null 필드는 건드리지 않습니다. */
    @Transactional
    public UserProfileResponse updateSettings(Long userId, Boolean emailNotificationEnabled) {
        User user = findActive(userId);
        if (emailNotificationEnabled != null) {
            user.changeEmailNotification(emailNotificationEnabled);
        }
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<SelectedItemResponse> getPreferences(Long userId, PreferenceType type) {
        return preferenceRepository.findSelectedWithName(userId, type).stream()
                .map(row -> new SelectedItemResponse(
                        ((Number) row[0]).longValue(),
                        (String) row[1]))
                .toList();
    }

    /**
     * USER-02 / 06 / 07.
     *
     * <p><b>최소 1개 검증은 서버가 스스로 판단합니다.</b>
     * 클라이언트가 "지금 온보딩 중" 이라고 알려주는 방식이면 그 값을 조작해 검증을 건너뛸 수 있습니다.
     * {@code onboarding_completed_at} 이 비어 있으면 아직 온보딩 중이라는 사실이
     * 이미 DB 에 있으므로 그것을 기준으로 삼습니다.
     */
    @Transactional
    public void updatePreferences(Long userId, PreferenceType type, Set<Long> ids) {
        User user = findActive(userId);   // 탈퇴 계정이 개인화를 수정하지 못하게 막습니다

        boolean onboarding = !user.isOnboardingCompleted();
        if (onboarding && (ids == null || ids.isEmpty())) {
            throw new BusinessException(ErrorCode.ONBOARDING_MIN_SELECTION);
        }
        preferenceRepository.replace(userId, type, ids == null ? Set.of() : ids);
    }

    /**
     * USER-05 온보딩 완료.
     *
     * <p>각 단계는 이미 저장되어 있고 여기서는 완료 시각만 찍습니다.
     * 이미 완료한 사용자가 다시 호출해도 조용히 넘어갑니다(멱등).
     */
    @Transactional
    public void completeOnboarding(Long userId) {
        User user = findActive(userId);
        user.completeOnboarding();
    }

    /**
     * USER-03 회원 탈퇴 — soft delete.
     *
     * <p><b>왜 여기서 다른 도메인 테이블을 지우는가</b>
     * 탈퇴는 계정 생명주기 작업이라 user 도메인이 소유합니다.
     * 도메인 서비스를 거치면 {@code bookmark -> user} 참조 방향이 뒤집혀 순환이 되고,
     * 개별 조회 후 삭제하면 불필요한 쿼리가 늘어납니다.
     * 한 트랜잭션에서 native 로 정리하는 것이 정확하고 빠릅니다.
     *
     * <p><b>왜 명시적으로 지워야 하는가</b>
     * soft delete 라 행이 남으므로 FK CASCADE 가 발동하지 않습니다.
     * 북마크·좋아요를 남기면 카운터가 부풀어 인기 공지 정렬이 왜곡됩니다.
     *
     * <p>댓글은 유지합니다. 지우면 답글 스레드가 무너집니다.
     * 작성자 표시는 SYS-05 개인정보 마스킹 이후 "탈퇴한 사용자"로 렌더링합니다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = findActive(userId);
        requireNotAdmin(user);

        for (PreferenceType type : PreferenceType.values()) {
            preferenceRepository.deleteAll(userId, type);
        }
        executeDelete("DELETE FROM bookmarks     WHERE user_id = :userId", userId);
        executeDelete("DELETE FROM article_likes WHERE user_id = :userId", userId);
        executeDelete("DELETE FROM notifications WHERE user_id = :userId", userId);

        user.withdraw();
    }

    /**
     * 관리자는 스스로 탈퇴할 수 없습니다.
     *
     * <p>ADM-16 이 "자기 자신의 권한은 못 바꾼다" 로 지키려는 불변식 —
     * <b>활성 관리자는 최소 한 명 남는다</b> — 이 탈퇴 경로로 그대로 우회됩니다.
     * 탈퇴해도 {@code role} 은 ADMIN 인 채 {@code status} 만 WITHDRAWN 이 되므로,
     * 마지막 관리자가 탈퇴하면 <b>활성 관리자가 0명</b>이 됩니다.
     * 그 상태에서는 권한을 되돌릴 API 를 쓸 수 있는 사람이 없어 DB 를 직접 고쳐야 합니다.
     *
     * <p>게다가 {@code uk_users_active_email} 이 {@code status='ACTIVE'} 부분 유니크라
     * 그 사람이 다시 구글 로그인하면 {@code findByEmailAndStatus(email, ACTIVE)} 가 비어
     * <b>{@code role='USER'} 인 새 계정</b>이 만들어집니다. 관리자 권한은 영영 돌아오지 않습니다.
     *
     * <p>"관리자가 한 명뿐일 때만 막기" 로 세지 않는 이유는 그 판정이 경합에 취약하기 때문입니다.
     * 두 관리자가 동시에 탈퇴하면 둘 다 "나 말고 한 명 더 있다" 를 보고 통과합니다.
     * 자기 자신을 무조건 막으면 순서와 무관하게 최소 한 명이 남습니다 — ADM-16 과 같은 논리입니다.
     */
    private static void requireNotAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(
                    ErrorCode.CANNOT_CHANGE_OWN_ROLE,
                    "관리자 권한을 가진 계정은 탈퇴할 수 없습니다. "
                            + "다른 관리자에게 권한 해제를 요청한 뒤 다시 시도해 주세요.");
        }
    }

    private void executeDelete(String sql, Long userId) {
        em.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
    }

    private User findActive(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
