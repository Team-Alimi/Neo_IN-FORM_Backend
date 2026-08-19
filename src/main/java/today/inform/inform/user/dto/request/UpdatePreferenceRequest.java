package today.inform.inform.user.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * USER-02 / 06 / 07 공통. <b>최종 목록</b>을 보냅니다.
 *
 * <p>서버가 기존 선택과 비교해 delta 만 반영합니다(전체 삭제 후 재삽입 금지).
 * 빈 목록도 허용합니다 — 마이페이지에서 전부 해제할 수 있어야 합니다.
 * 온보딩 진행 중의 최소 개수 제한은 별도 파라미터로 전달합니다.
 */
public record UpdatePreferenceRequest(
        @NotNull(message = "ids 는 필수입니다.")
        Set<Long> ids) {
}
