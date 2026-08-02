package today.inform.inform.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이징 응답 공통 형태. (API_SPEC_V2 2.5 참조)
 * { "content": [...], "page": { "number", "size", "total_elements", "total_pages", "has_next" } }
 */
public record PageResponse<T>(List<T> content, PageInfo page) {

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                new PageInfo(
                        source.getNumber(),
                        source.getSize(),
                        source.getTotalElements(),
                        source.getTotalPages(),
                        source.hasNext()
                )
        );
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }
}
