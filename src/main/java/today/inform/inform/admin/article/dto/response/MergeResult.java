package today.inform.inform.admin.article.dto.response;

import java.util.List;

/**
 * ADM-13 병합 결과 (명세 4.8).
 *
 * <p><b>무엇이 몇 건 옮겨졌는지를 함께 돌려줍니다.</b> 병합은 되돌릴 수 없고
 * 흡수된 공지는 사라지므로, 관리자가 그 자리에서 "예상한 만큼 옮겨졌나" 를 확인할 수 있어야 합니다.
 * 개수가 0 이면 무언가 잘못된 것인데, 응답이 성공 여부만 알려 주면 알아챌 방법이 없습니다.
 *
 * @param merged 실제로 흡수된 공지 번호
 */
public record MergeResult(Long targetId, List<Long> merged, Moved moved) {

    /** 옮겨진 것들의 건수. 이름은 명세의 키를 그대로 씁니다. */
    public record Moved(int vendors, int bookmarks, int attachments, int categories,
                        int comments, int notifications) {
    }
}
