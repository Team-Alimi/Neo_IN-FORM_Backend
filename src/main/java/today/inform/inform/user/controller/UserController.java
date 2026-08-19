package today.inform.inform.user.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.auth.service.AuthService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.security.AuthPrincipal;
import today.inform.inform.user.dto.request.UpdatePreferenceRequest;
import today.inform.inform.user.dto.request.UpdateUserSettingsRequest;
import today.inform.inform.user.dto.response.SelectedItemResponse;
import today.inform.inform.user.dto.response.UserProfileResponse;
import today.inform.inform.user.repository.PreferenceType;
import today.inform.inform.user.service.UserService;

/**
 * USER-01 ~ USER-07. 전부 로그인 필요.
 *
 * <p>경로에 {@code /api/v1} 을 쓰지 않습니다 — {@code WebConfig} 가 모든 RestController 에 붙입니다.
 *
 * <p>대상 id 는 URL 로 받지 않고 항상 토큰의 {@code principal.userId()} 를 씁니다.
 * {@code /users/{id}} 형태였다면 남의 id 를 넣는 접근을 매번 막아야 합니다.
 */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /** USER-01 */
    @GetMapping
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(userService.getProfile(principal.userId()));
    }

    /** USER-04 — 부분 수정이라 PATCH 입니다. */
    @PatchMapping
    public ApiResponse<UserProfileResponse> updateSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        return ApiResponse.success(
                userService.updateSettings(principal.userId(), request.emailNotificationEnabled()));
    }

    /**
     * USER-03 회원 탈퇴.
     *
     * <p>세션 정리를 서비스가 아니라 여기서 합니다. Redis 는 트랜잭션에 참여하지 않으므로
     * 서비스 안에서 지우면 이후 롤백 시 <b>탈퇴는 취소됐는데 토큰만 사라진</b> 상태가 됩니다.
     * 커밋이 끝난 뒤 지우는 편이 안전합니다.
     *
     * <p>이 호출이 실패해도 계정은 이미 탈퇴 처리됐고, {@code AuthService.refresh()} 가
     * 활성 사용자만 통과시키므로 남은 refresh token 으로는 아무것도 할 수 없습니다.
     */
    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthPrincipal principal) {
        userService.withdraw(principal.userId());
        authService.logoutAll(principal.userId());
        return ApiResponse.success(null);
    }

    /** USER-02 */
    @GetMapping("/vendors")
    public ApiResponse<List<SelectedItemResponse>> getVendors(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(userService.getPreferences(principal.userId(), PreferenceType.VENDOR));
    }

    /** USER-02 — 최종 목록을 받아 서버가 delta 로 반영합니다. */
    @PutMapping("/vendors")
    public ApiResponse<List<SelectedItemResponse>> updateVendors(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return update(principal.userId(), PreferenceType.VENDOR, request);
    }

    /** USER-06 */
    @GetMapping("/interests/categories")
    public ApiResponse<List<SelectedItemResponse>> getInterestCategories(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(userService.getPreferences(principal.userId(), PreferenceType.CATEGORY));
    }

    /** USER-06 */
    @PutMapping("/interests/categories")
    public ApiResponse<List<SelectedItemResponse>> updateInterestCategories(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return update(principal.userId(), PreferenceType.CATEGORY, request);
    }

    /** USER-07 */
    @GetMapping("/interests/club-types")
    public ApiResponse<List<SelectedItemResponse>> getInterestClubTypes(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(userService.getPreferences(principal.userId(), PreferenceType.CLUB_TYPE));
    }

    /** USER-07 */
    @PutMapping("/interests/club-types")
    public ApiResponse<List<SelectedItemResponse>> updateInterestClubTypes(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return update(principal.userId(), PreferenceType.CLUB_TYPE, request);
    }

    /** USER-05 온보딩 완료. 각 단계는 위 PUT 으로 이미 저장된 상태입니다. */
    @PostMapping("/onboarding/complete")
    public ApiResponse<UserProfileResponse> completeOnboarding(
            @AuthenticationPrincipal AuthPrincipal principal) {
        userService.completeOnboarding(principal.userId());
        return ApiResponse.success(userService.getProfile(principal.userId()));
    }

    /**
     * 저장 후 갱신된 목록을 돌려줍니다.
     * 클라이언트가 GET 을 다시 호출하지 않아도 되고, 서버가 실제로 무엇을 저장했는지도 드러납니다.
     */
    private ApiResponse<List<SelectedItemResponse>> update(
            Long userId, PreferenceType type, UpdatePreferenceRequest request) {
        userService.updatePreferences(userId, type, request.ids());
        return ApiResponse.success(userService.getPreferences(userId, type));
    }
}
