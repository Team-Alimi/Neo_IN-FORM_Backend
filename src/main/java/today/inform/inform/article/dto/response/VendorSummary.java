package today.inform.inform.article.dto.response;

import today.inform.inform.article.entity.SourceType;

/**
 * 공통 응답 객체 (명세 2.8).
 *
 * <p><b>{@code initial} 을 내보내는 것은 이 목록의 예외입니다.</b>
 * 사용자용 제공처 목록(COM-01)에서는 크롤러 계약 키라 감추지만,
 * 공지에 붙는 출처 표시는 화면이 학과를 축약해 그리기 때문에 필요합니다
 * ("컴퓨터공학과" 대신 "컴공" 칩). 명세 2.8 이 그렇게 규정합니다.
 */
public record VendorSummary(Long id, String name, String initial, SourceType type) {
}
