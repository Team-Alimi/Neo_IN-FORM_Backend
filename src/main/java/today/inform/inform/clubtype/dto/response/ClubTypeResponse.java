package today.inform.inform.clubtype.dto.response;

import java.util.List;
import today.inform.inform.clubtype.entity.ClubType;

/**
 * 동아리 유형 한 건.
 *
 * <p><b>{@code code} 를 내보내지 않습니다.</b> {@link
 * today.inform.inform.category.dto.response.CategoryResponse} 와 같은 이유입니다 —
 * 내보내면 프론트가 그 문자열에 로직을 걸기 시작하고, 그러면 표시명조차 못 바꾸게 됩니다.
 * 표시는 {@code name}, 식별은 {@code id} 입니다.
 */
public record ClubTypeResponse(Long id, String name, int sortOrder) {

    public static ClubTypeResponse from(ClubType clubType) {
        return new ClubTypeResponse(clubType.getId(), clubType.getName(), clubType.getSortOrder());
    }

    public static List<ClubTypeResponse> from(List<ClubType> clubTypes) {
        return clubTypes.stream().map(ClubTypeResponse::from).toList();
    }
}
