package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.LoginRequest;
import today.inform.inform_backend.dto.LoginResponse;
import today.inform.inform_backend.entity.SocialType;
import today.inform.inform_backend.service.auth.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(SocialType.GOOGLE, request.getIdToken());
        return ApiResponse.success(response);
    }
}
