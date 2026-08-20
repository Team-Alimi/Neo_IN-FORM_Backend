package today.inform.inform.admin.article.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * ADM-08 휴지통 이동 / ADM-09 복구 공통.
 *
 * <p>상한을 두는 이유는 잠금 때문입니다. 한 요청이 공지 수천 건을 건드리면
 * 그동안 그 행들에 대한 크롤러 갱신이 전부 대기합니다.
 */
public record ArticleIdsRequest(
        @NotEmpty(message = "대상 공지를 선택해 주세요.")
        @Size(max = 200, message = "한 번에 200건까지 처리할 수 있습니다.")
        List<@NotNull(message = "공지 번호가 비어 있습니다.") Long> articleIds,

        @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
        String memo) {
}
