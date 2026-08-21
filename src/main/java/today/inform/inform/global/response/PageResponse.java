package today.inform.inform.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이징 응답 봉투 (명세 공통 규격).
 *
 * <p><b>페이지 번호가 1부터입니다.</b> Spring 의 {@link Page#getNumber()} 는 0부터 세지만,
 * 그대로 내보내면 화면을 그리는 쪽이 "3페이지를 보여 주려면 2를 보낸다" 는 규칙을 항상 기억해야 합니다.
 * 이 변환을 클라이언트마다 따로 하면 결국 한 곳은 빠뜨립니다 — 경계에서 첫 페이지가 두 번 보이거나
 * 마지막 페이지가 사라지는데, 둘 다 에러 없이 조용히 일어납니다.
 * 그래서 <b>경계에서 한 번만</b> 변환합니다.
 *
 * <p>요청도 같은 규칙이어야 짝이 맞습니다 —
 * {@code spring.data.web.pageable.one-indexed-parameters: true} 가 {@code ?page=1} 을 0 으로 되돌립니다.
 * 둘 중 하나만 켜면 응답과 요청이 한 칸씩 어긋납니다.
 *
 * <p><b>{@code totalItems} 는 왜 {@code totalArticles} 가 아닌가</b>
 * 이 봉투는 공지뿐 아니라 댓글·회원·알림 목록도 씁니다. 필드 이름에 공지를 박으면
 * {@code GET /admin/users} 응답에 {@code total_articles} 가 나옵니다.
 */
public record PageResponse<T>(List<T> content, PageInfo pageInfo) {

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                new PageInfo(
                        source.getNumber() + 1,
                        source.getSize(),
                        source.getTotalPages(),
                        source.getTotalElements(),
                        source.hasNext()
                )
        );
    }

    /**
     * @param currentPage 1부터. 결과가 0건이어도 1 입니다 — 0 을 내보내면 화면이 "0페이지" 를 그립니다
     * @param totalPages  전체 페이지 수. 0건이면 0
     * @param totalItems  전체 건수
     * @param hasNext     다음 페이지 존재 여부
     */
    public record PageInfo(int currentPage, int size, int totalPages, long totalItems, boolean hasNext) {
    }
}
