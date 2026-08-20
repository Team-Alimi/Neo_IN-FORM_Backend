package today.inform.inform.admin.article.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import today.inform.inform.article.entity.ArticleStatus;

/**
 * ADM-07 상태 일괄 변경.
 *
 * @param memo 변경 사유. 감사 로그에 남습니다.
 *             전달 방법이 특이한데({@code app.status_change_memo} GUC),
 *             기록하는 주체가 앱이 아니라 <b>DB 트리거</b>이기 때문입니다.
 *             트리거는 "왜" 를 알 수 없어서 트랜잭션 변수로 넘겨줘야 합니다.
 */
public record ChangeStatusRequest(
        @NotEmpty(message = "대상 공지를 선택해 주세요.")
        @Size(max = 200, message = "한 번에 200건까지 처리할 수 있습니다.")
        List<@NotNull(message = "공지 번호가 비어 있습니다.") Long> articleIds,

        @NotNull(message = "변경할 상태를 지정해 주세요.")
        ArticleStatus status,

        @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
        String memo) {
}
