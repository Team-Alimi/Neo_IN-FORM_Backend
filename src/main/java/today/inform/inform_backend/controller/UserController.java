package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.UserUpdateRequest;
import today.inform.inform_backend.service.user.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // TODO: 추후 SecurityContext에서 userId를 추출하도록 리팩토링 필요
    @PatchMapping("/{userId}/major")
    public ApiResponse<Void> updateMajor(
            @PathVariable Integer userId,
            @RequestBody UserUpdateRequest request
    ) {
        userService.updateMajor(userId, request.getMajorId());
        return ApiResponse.success(null);
    }
}
