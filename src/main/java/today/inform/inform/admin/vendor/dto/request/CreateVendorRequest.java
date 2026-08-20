package today.inform.inform.admin.vendor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.vendor.entity.Vendor;

/**
 * VND-01 제공처 등록.
 *
 * <p><b>D7 규약의 1단계입니다.</b> 여기서 등록한 뒤 크롤러 시드에
 * {@code "vendor": "<initial>"} 을 추가합니다. 순서가 반대면 크롤러가 없는 제공처를 찾다가
 * 그 학과 공지를 통째로 버립니다.
 *
 * @param initial 크롤러 시드가 참조하는 business key. <b>등록 후에는 바꿀 수 없습니다.</b>
 * @param type    SCHOOL(학과·기관) 또는 CLUB(동아리). <b>등록 후에는 바꿀 수 없습니다</b> —
 *                {@code article_vendors} 교차 검증(IN002)의 전제입니다
 */
public record CreateVendorRequest(
        @NotBlank(message = "제공처 이름을 입력해 주세요.")
        @Size(max = Vendor.NAME_MAX_LENGTH, message = "제공처 이름은 100자를 넘을 수 없습니다.")
        String name,

        @NotBlank(message = "크롤러 식별자(initial)를 입력해 주세요.")
        @Size(max = Vendor.INITIAL_MAX_LENGTH, message = "크롤러 식별자는 100자를 넘을 수 없습니다.")
        String initial,

        @NotNull(message = "제공처 유형을 선택해 주세요.")
        SourceType type,

        @Size(max = Vendor.HOMEPAGE_URL_MAX_LENGTH, message = "홈페이지 주소는 500자를 넘을 수 없습니다.")
        String homepageUrl) {
}
