package today.inform.inform_backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminStatus {
    INSPECTED_YET("미검수"),
    REFLECTION_WAITING("반영대기"),
    SUSPECTED_DUPLICATE("중복의심"),
    GARBAGE("휴지통");

    private final String description;
}
