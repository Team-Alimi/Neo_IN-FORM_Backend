package today.inform.inform.user.entity;

/**
 * 탈퇴는 soft delete 입니다. 행을 지우지 않고 WITHDRAWN 으로 바꿉니다.
 * DB CHECK 가 {@code (status='WITHDRAWN') = (withdrawn_at IS NOT NULL)} 을 강제합니다.
 */
public enum UserStatus {
    ACTIVE,
    WITHDRAWN
}
