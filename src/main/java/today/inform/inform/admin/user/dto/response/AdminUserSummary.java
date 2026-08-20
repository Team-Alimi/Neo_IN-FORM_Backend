package today.inform.inform.admin.user.dto.response;

import java.time.OffsetDateTime;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;
import today.inform.inform.user.entity.UserStatus;

/**
 * 관리자 회원 목록 한 줄.
 *
 * <p><b>이름과 이메일을 가리지 않습니다.</b> 탈퇴한 사용자도 마찬가지입니다 —
 * 댓글 화면에서는 "탈퇴한 사용자" 로 감추지만, 여기는 권한을 줄 대상을 <b>사람으로 특정</b>해야 하는
 * 화면이라 가리면 기능 자체가 성립하지 않습니다. {@code /admin/**} 은 관리자 전용입니다.
 *
 * @param status 탈퇴 여부. 탈퇴한 계정에는 관리자 권한을 줄 수 없습니다
 */
public record AdminUserSummary(
        Long id,
        String email,
        String name,
        UserRole role,
        UserStatus status,
        boolean onboardingCompleted,
        OffsetDateTime createdAt,
        OffsetDateTime withdrawnAt) {

    public static AdminUserSummary from(User user) {
        return new AdminUserSummary(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                user.isOnboardingCompleted(),
                user.getCreatedAt(),
                user.getWithdrawnAt());
    }
}
