package today.inform.inform.admin.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.admin.user.dto.request.ChangeRoleRequest;
import today.inform.inform.admin.user.dto.response.AdminUserSummary;
import today.inform.inform.admin.user.service.AdminUserService;
import today.inform.inform.auth.service.AuthService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.response.PageResponse;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.user.entity.UserRole;
import today.inform.inform.user.entity.UserStatus;

/** ADM-16 회원 역할 관리. {@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AuthService authService;

    /**
     * 회원 목록.
     *
     * @param keyword 이메일·이름 부분 일치. 2글자 미만은 무시합니다
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminUserSummary>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "role", required = false) UserRole role,
            @RequestParam(name = "status", required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(
                adminUserService.search(keyword, role, status, pageable)));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserSummary> detail(@PathVariable Long userId) {
        return ApiResponse.success(adminUserService.get(userId));
    }

    /**
     * ADM-16 권한 변경.
     *
     * <p><b>토큰 무효화를 서비스가 아니라 여기서 합니다.</b> Redis 는 트랜잭션에 참여하지 않으므로
     * 서비스 안에서 부르면 이후 롤백 시 <b>권한은 그대로인데 세션만 끊긴</b> 상태가 됩니다.
     * 커밋이 끝난 뒤가 안전합니다. (회원 탈퇴 {@code UserController#withdraw} 와 같은 이유)
     *
     * <p>실제로 권한이 바뀐 경우에만 부릅니다. 같은 권한을 다시 보낸 요청까지 세션을 끊으면
     * 목록에서 실수로 한 번 더 누른 것만으로 남이 로그아웃당합니다.
     */
    @PatchMapping("/{userId}/role")
    public ApiResponse<AdminUserSummary> changeRole(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request) {

        if (adminUserService.changeRole(userId, request.role(), principal.userId())) {
            authService.revokeAccessTokens(userId);
        }
        return ApiResponse.success(adminUserService.get(userId));
    }
}
