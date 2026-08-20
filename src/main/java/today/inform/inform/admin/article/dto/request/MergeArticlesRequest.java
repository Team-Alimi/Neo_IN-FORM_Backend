package today.inform.inform.admin.article.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * ADM-13 중복 공지 병합.
 *
 * @param targetId  남길 공지
 * @param sourceIds 흡수될 공지. <b>병합 후 삭제됩니다</b>
 * @param memo      병합 사유. 대상 공지의 상태 이력에 남습니다 —
 *                  나중에 "이 공지에 왜 남의 댓글이 있지" 를 추적할 유일한 단서입니다
 */
public record MergeArticlesRequest(
        @NotNull(message = "남길 공지를 지정해 주세요.")
        Long targetId,

        @NotEmpty(message = "흡수할 공지를 선택해 주세요.")
        @Size(max = 20, message = "한 번에 20건까지 병합할 수 있습니다.")
        List<@NotNull(message = "공지 번호가 비어 있습니다.") Long> sourceIds,

        @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
        String memo) {
}
