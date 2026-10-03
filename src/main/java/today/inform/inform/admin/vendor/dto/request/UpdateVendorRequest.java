package today.inform.inform.admin.vendor.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.vendor.entity.Vendor;

/**
 * VND-02 수정 · VND-03 비활성화.
 *
 * <p><b>부분 수정입니다.</b> {@code null} 은 "이 항목은 그대로 두라" 는 뜻입니다.
 * 공지 수정(ADM-05)이 전체 교체인 것과 반대인데, 제공처는 항목이 몇 개 안 되고
 * 목록 화면에서 토글 하나만 바꾸는 조작이 흔하기 때문입니다.
 *
 * <p>그래서 <b>홈페이지 주소를 지우려면 빈 문자열</b>을 보내야 합니다.
 * {@code null} 로는 지울 수 없습니다 — 그게 "그대로 두라" 와 구분되지 않기 때문입니다.
 *
 * @param initial 바꿀 수 없습니다. 현재 값과 같으면 무시하고, 다르면 400 으로 거부합니다.
 *                받는 이유는 관리 화면이 폼 전체를 되돌려 보내는 흔한 구현을 막지 않기 위해서입니다
 * @param type    같은 이유로 받고, 같은 규칙으로 다룹니다
 * @param clubTypeIds 동아리 유형. {@code null} 이면 유지, 보내면 전체 교체입니다
 */
public record UpdateVendorRequest(
        @Size(max = Vendor.NAME_MAX_LENGTH, message = "제공처 이름은 100자를 넘을 수 없습니다.")
        String name,

        @Size(max = Vendor.HOMEPAGE_URL_MAX_LENGTH, message = "홈페이지 주소는 500자를 넘을 수 없습니다.")
        String homepageUrl,

        Boolean isActive,

        @Size(max = Vendor.INITIAL_MAX_LENGTH, message = "크롤러 식별자는 100자를 넘을 수 없습니다.")
        String initial,

        SourceType type,

        /*
         * 동아리 유형. null 이면 "그대로 두라" 이고, 보내면 전체 교체입니다.
         * 이 필드만 다른 항목과 달리 배열이라 "빈 배열로 지우기" 가 가능해 보이지만,
         * CLUB 은 유형이 필수라 빈 배열은 400 입니다. 유형을 떼고 싶으면 다른 유형으로 바꾸세요.
         */
        List<@NotNull(message = "동아리 유형 번호가 비어 있습니다.") Long> clubTypeIds) {
}
