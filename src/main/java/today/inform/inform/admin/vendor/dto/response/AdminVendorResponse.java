package today.inform.inform.admin.vendor.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.vendor.entity.Vendor;

/**
 * 관리자 제공처 한 건.
 *
 * @param warning 관리자가 알아야 할 주의. 없으면 {@code null} 입니다.
 *                오류가 아니라서 상태 코드로는 전달할 수 없는데, 놓치면 수집이 조용히 멈추는
 *                종류의 정보라 응답에 실어 보냅니다
 */
public record AdminVendorResponse(
        Long id,
        String name,
        String initial,
        SourceType type,
        String homepageUrl,
        boolean isActive,
        OffsetDateTime createdAt,
        String warning) {

    public static AdminVendorResponse from(Vendor vendor) {
        return of(vendor, null);
    }

    public static AdminVendorResponse of(Vendor vendor, String warning) {
        return new AdminVendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getInitial(),
                vendor.getType(),
                vendor.getHomepageUrl(),
                vendor.isActive(),
                vendor.getCreatedAt(),
                warning);
    }

    public static List<AdminVendorResponse> from(List<Vendor> vendors) {
        return vendors.stream().map(AdminVendorResponse::from).toList();
    }
}
