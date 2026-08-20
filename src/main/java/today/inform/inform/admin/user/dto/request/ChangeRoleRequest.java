package today.inform.inform.admin.user.dto.request;

import jakarta.validation.constraints.NotNull;
import today.inform.inform.user.entity.UserRole;

/**
 * ADM-16 회원 역할 변경.
 *
 * <p>변경 사실은 DB 트리거({@code trg_users_90_role_audit})가 {@code user_role_logs} 에
 * 같은 트랜잭션으로 기록합니다. 앱이 로그를 따로 쓰지 않고, 따라서 <b>빠뜨릴 수도 없습니다.</b>
 */
public record ChangeRoleRequest(
        @NotNull(message = "변경할 권한을 선택해 주세요.")
        UserRole role) {
}
