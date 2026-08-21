package today.inform.inform.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import today.inform.inform.global.response.PageResponse;

/**
 * 페이징 봉투의 경계 변환.
 *
 * <p>여기서 틀리면 화면이 한 칸씩 밀리는데 <b>에러가 나지 않습니다</b> —
 * 목록은 정상으로 보이고 경계에서만 중복·누락이 생깁니다. 그래서 단언으로 못 박아 둡니다.
 */
class PageResponseTest {

    @Test
    @DisplayName("★ 첫 페이지는 0 이 아니라 1 로 나간다 — 명세가 1부터 세기 때문")
    void firstPageIsOne() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 48));

        assertThat(response.pageInfo().currentPage())
                .as("Spring 의 0-based 를 그대로 내보내면 프론트가 매번 +1 해야 하고, 한 곳은 반드시 빠뜨립니다")
                .isEqualTo(1);
        assertThat(response.pageInfo().size()).isEqualTo(20);
        assertThat(response.pageInfo().totalItems()).isEqualTo(48);
        assertThat(response.pageInfo().totalPages()).isEqualTo(3);
        assertThat(response.pageInfo().hasNext()).isTrue();
        assertThat(response.content()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("마지막 페이지는 has_next 가 false 다")
    void lastPageHasNoNext() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("c"), PageRequest.of(2, 20), 41));

        assertThat(response.pageInfo().currentPage()).isEqualTo(3);
        assertThat(response.pageInfo().totalPages()).isEqualTo(3);
        assertThat(response.pageInfo().hasNext()).isFalse();
    }

    @Test
    @DisplayName("결과가 없어도 current_page 는 1 이다 — 화면이 '0페이지' 를 그리면 안 된다")
    void emptyResultStillStartsAtOne() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.pageInfo().currentPage()).isEqualTo(1);
        assertThat(response.pageInfo().totalPages()).isZero();
        assertThat(response.pageInfo().totalItems()).isZero();
        assertThat(response.pageInfo().hasNext()).isFalse();
        assertThat(response.content()).isEmpty();
    }
}
