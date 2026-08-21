package today.inform.inform.vendor.dto.response;

import java.util.List;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.vendor.entity.Vendor;

/**
 * COM-01 제공처 한 건.
 *
 * <p><b>{@code initial} 을 내보내지 않습니다.</b> 그 값은 크롤러와 주고받는 내부 계약 키이고,
 * 화면이 쓸 일이 없습니다. 내보내면 클라이언트가 그것으로 제공처를 식별하기 시작하는데,
 * 그러면 내부 계약을 바꿀 때 프론트까지 함께 깨집니다.
 * {@code is_active} 도 없습니다 — 이 목록에는 활성만 담기므로 항상 true 입니다.
 */
public record VendorResponse(
        Long id,
        String name,
        SourceType type,
        String homepageUrl) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(), vendor.getName(), vendor.getType(), vendor.getHomepageUrl());
    }

    public static List<VendorResponse> from(List<Vendor> vendors) {
        return vendors.stream().map(VendorResponse::from).toList();
    }
}
