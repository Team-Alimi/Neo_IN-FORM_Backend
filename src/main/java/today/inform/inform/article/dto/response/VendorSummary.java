package today.inform.inform.article.dto.response;

import java.util.List;
import today.inform.inform.article.entity.SourceType;

/**
 * 공통 응답 객체 (명세 2.8).
 *
 * <p><b>{@code initial} 을 내보내는 것은 이 목록의 예외입니다.</b>
 * 사용자용 제공처 목록(COM-01)에서는 크롤러 계약 키라 감추지만,
 * 공지에 붙는 출처 표시는 화면이 학과를 축약해 그리기 때문에 필요합니다
 * ("컴퓨터공학과" 대신 "컴공" 칩). 명세 2.8 이 그렇게 규정합니다.
 */
public record VendorSummary(Long id, String name, String initial, SourceType type,
                            List<ClubTypeRef> clubTypes) {

    /**
     * 동아리 유형. <b>CLUB 제공처에만 채워집니다</b> — 학과·기관은 항상 빈 배열입니다.
     *
     * <p>이름까지 담는 이유는 화면이 해시태그를 바로 그려야 하기 때문입니다.
     * id 만 주면 프론트가 유형 목록과 조인해야 하는데, 그 목록에는 <b>접힌 유형이 없어</b>
     * 비활성 유형이 붙은 동아리에서 이름을 찾지 못합니다.
     */
    public record ClubTypeRef(Long id, String name) {
    }
}
