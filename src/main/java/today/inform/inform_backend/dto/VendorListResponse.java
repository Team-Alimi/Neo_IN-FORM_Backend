package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VendorListResponse {
    private Integer vendorId;
    private String vendorName;
    private String vendorInitial;
    private String vendorType;
}
