package today.inform.inform.clubtype.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.clubtype.dto.response.ClubTypeResponse;
import today.inform.inform.clubtype.service.ClubTypeQueryService;
import today.inform.inform.global.response.ApiResponse;

/**
 * 동아리 유형 목록. <b>비로그인도 열립니다</b> — 온보딩 화면이 이걸로 그려지는데,
 * 온보딩은 로그인 직후 토큰을 받기 전 단계에서도 화면을 그려야 할 수 있습니다.
 *
 * <p>사용자가 <b>고른 것</b>은 {@code GET /users/me/interests/club-types} 입니다.
 * 이쪽은 <b>고를 수 있는 전체 목록</b>이라 별개입니다 —
 * {@code /categories} 와 {@code /users/me/interests/categories} 의 관계와 같습니다.
 */
@RestController
@RequestMapping("/club-types")
@RequiredArgsConstructor
public class ClubTypeController {

    private final ClubTypeQueryService clubTypeQueryService;

    @GetMapping
    public ApiResponse<List<ClubTypeResponse>> list() {
        return ApiResponse.success(clubTypeQueryService.findActive());
    }
}
