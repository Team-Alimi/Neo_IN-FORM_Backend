package today.inform.inform.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.auth.dto.request.GoogleLoginRequest;
import today.inform.inform.auth.dto.request.RefreshTokenRequest;
import today.inform.inform.auth.dto.response.TokenResponse;
import today.inform.inform.auth.service.AuthService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * 경로에 {@code /api/v1} 을 쓰지 않습니다 — {@code WebConfig} 가 모든 RestController 에 붙입니다.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** AUTH-01 — 공개 */
    @PostMapping("/login/google")
    public ApiResponse<TokenResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success(authService.loginWithGoogle(request.idToken()));
    }

    /** AUTH-02 — 공개 (access token 이 이미 만료된 상태에서 호출되므로) */
    @PostMapping("/token/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    /** AUTH-03 — 로그인 필요. 어느 기기를 끊을지는 body 의 refresh token 이 지목합니다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null);
    }

    /** AUTH-04 — 로그인 필요 */
    @PostMapping("/logout/all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logoutAll(principal.userId());
        return ApiResponse.success(null);
    }
}
