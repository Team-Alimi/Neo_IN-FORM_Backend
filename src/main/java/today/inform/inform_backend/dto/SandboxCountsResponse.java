package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SandboxCountsResponse {
    private Long inspectedYet;
    private Long reflectionWaiting;
    private Long suspectedDuplicate;
    private Long garbage;
}
