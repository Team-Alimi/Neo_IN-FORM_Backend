package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform_backend.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<String>> checkAdmin() {
        return ResponseEntity.ok(ApiResponse.success("관리자 권한 확인 성공"));
    }

    // 추가적인 관리자 전용 기능 (예: 로그 조회 등)을 이곳에 구현할 수 있습니다.
}
