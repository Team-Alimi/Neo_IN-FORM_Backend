package today.inform.inform.admin.article.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 벌크 작업 공통 요청 (명세 4.8).
 *
 * <p>키가 {@code ids} 입니다 — {@code article_ids} 가 아닙니다.
 * 경로가 이미 {@code /admin/articles/bulk/*} 라 무엇의 id 인지는 경로가 말해 줍니다.
 *
 * <p>상한을 두는 이유는 잠금입니다. 한 요청이 공지 수천 건을 건드리면
 * 그동안 그 행들에 대한 크롤러 갱신이 전부 대기합니다.
 */
public record ArticleIdsRequest(
        @NotEmpty(message = "대상 공지를 선택해 주세요.")
        @Size(max = 200, message = "한 번에 200건까지 처리할 수 있습니다.")
        List<@NotNull(message = "공지 번호가 비어 있습니다.") Long> ids,

        @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
        String memo) {
}
