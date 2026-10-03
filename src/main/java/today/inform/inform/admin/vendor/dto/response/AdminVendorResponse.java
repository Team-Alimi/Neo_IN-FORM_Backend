package today.inform.inform.admin.vendor.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
        List<ClubTypeRef> clubTypes,
        String warning) {

    /**
     * 동아리 유형 한 건. 이름까지 담는 이유는 수정 화면이 기존 선택을 그려야 하기 때문입니다 —
     * id 만 주면 프론트가 유형 목록과 조인해야 하고, 그 목록에는 <b>비활성 유형이 없어</b>
     * 접힌 유형이 붙은 동아리에서 이름을 찾지 못합니다.
     */
    public record ClubTypeRef(Long id, String name) {
    }

    /** 학과·기관처럼 유형이 없는 제공처용. */
    public static AdminVendorResponse from(Vendor vendor) {
        return of(vendor, List.of(), null);
    }

    public static AdminVendorResponse of(Vendor vendor, List<ClubTypeRef> clubTypes, String warning) {
        return new AdminVendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getInitial(),
                vendor.getType(),
                vendor.getHomepageUrl(),
                vendor.isActive(),
                vendor.getCreatedAt(),
                clubTypes == null ? List.of() : clubTypes,
                warning);
    }

    /** 목록용. 유형은 호출부가 한 번에 읽어 넘깁니다 — 항목마다 조회하면 N+1 입니다. */
    public static List<AdminVendorResponse> from(
            List<Vendor> vendors, Map<Long, List<ClubTypeRef>> clubTypesByVendor) {
        return vendors.stream()
                .map(vendor -> of(vendor,
                        clubTypesByVendor.getOrDefault(vendor.getId(), List.of()), null))
                .toList();
    }
}
