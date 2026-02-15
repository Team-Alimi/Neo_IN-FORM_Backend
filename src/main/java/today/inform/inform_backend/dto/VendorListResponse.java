package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class VendorListResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer vendorId;
    private String vendorName;
    private String vendorInitial;
    private String vendorType;
}
