package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.UserUpdateRequest;
import today.inform.inform_backend.service.user.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/{userId}/major")
    public ApiResponse<Void> updateMajor(
            @PathVariable Integer userId,
            @AuthenticationPrincipal Integer loginUserId, // SecurityContext에서 꺼내온 ID
            @RequestBody UserUpdateRequest request
    ) {
        // 보안 검증: 본인 확인
        if (!userId.equals(loginUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        userService.updateMajor(userId, request.getMajorId());
        return ApiResponse.success(null);
    }
}
