package today.inform.inform.admin.vendor.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.vendor.dto.request.CreateVendorRequest;
import today.inform.inform.admin.vendor.dto.request.UpdateVendorRequest;
import today.inform.inform.admin.vendor.dto.response.AdminVendorResponse;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.vendor.entity.Vendor;
import today.inform.inform.vendor.repository.VendorRepository;

/**
 * VND-01 등록 · VND-02 수정 · VND-03 비활성화.
 *
 * <p><b>이 도메인의 핵심은 {@code initial} 입니다.</b> 크롤러가 시드에 적힌 문자열로
 * 제공처를 찾기 때문에, 이 값 하나가 어긋나면 해당 학과 공지가 <b>오류 없이 안 들어옵니다.</b>
 * 그래서 등록·수정 모두 이 값을 중심으로 방어합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminVendorService {

    private final VendorRepository vendorRepository;

    /** 관리 화면 목록. 비활성 제공처도 보여야 다시 켤 수 있습니다. */
    @Transactional(readOnly = true)
    public List<AdminVendorResponse> search(SourceType type, Boolean active) {
        return AdminVendorResponse.from(vendorRepository.findForAdmin(type, active));
    }

    @Transactional(readOnly = true)
    public AdminVendorResponse get(Long vendorId) {
        return AdminVendorResponse.from(load(vendorId));
    }

    /**
     * VND-01 등록.
     *
     * <p>중복은 {@code initial} UNIQUE 가 최종 판정하지만, 그대로 두면 관리자가 받는 메시지가
     * "이미 존재하는 값입니다" 뿐이라 <b>이름이 겹친 건지 식별자가 겹친 건지</b> 알 수 없습니다.
     * 여기서 먼저 확인해 어느 값이 문제인지 알려 줍니다. UNIQUE 는 경합용 최후 방어로 남습니다.
     *
     * <p>중복 검사에 <b>엔티티가 정규화한 값</b>을 씁니다. 요청 원문으로 검사하면
     * 앞뒤 공백이 붙은 요청이 검사를 통과한 뒤 정규화되어 UNIQUE 에서 터집니다.
     */
    @Transactional
    public AdminVendorResponse create(CreateVendorRequest request) {
        Vendor vendor = Vendor.create(
                request.name(), request.initial(), request.type(), request.homepageUrl());

        if (vendorRepository.existsByInitial(vendor.getInitial())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "이미 쓰이고 있는 크롤러 식별자입니다: " + vendor.getInitial());
        }

        Vendor saved = vendorRepository.save(vendor);
        return AdminVendorResponse.of(saved, seedReminder(saved));
    }

    /**
     * VND-02 수정 · VND-03 비활성화.
     *
     * <p>{@code initial} 과 {@code type} 은 바꿀 수 없습니다.
     * 엔티티가 {@code updatable = false} 라 <b>보내도 UPDATE 문에 실리지 않지만</b>,
     * 그것만 믿고 두면 관리자가 200 을 받고 값이 바뀐 줄 압니다.
     * 그래서 여기서 명시적으로 거부합니다 — 같은 값이면 통과시켜, 폼 전체를 되돌려 보내는
     * 화면이 매번 400 을 받는 일은 없게 합니다.
     */
    @Transactional
    public AdminVendorResponse update(Long vendorId, UpdateVendorRequest request) {
        Vendor vendor = load(vendorId);

        rejectImmutableChange("크롤러 식별자(initial)", vendor.getInitial(), trimOrNull(request.initial()),
                "크롤러 시드가 이 값으로 제공처를 찾습니다. 바꾸면 다음 수집부터 이 제공처의 공지가 들어오지 않습니다.");
        rejectImmutableChange("제공처 유형(type)", vendor.getType(), request.type(),
                "이미 연결된 공지들의 출처 검증 전제라 소급으로 깨집니다.");

        if (request.name() != null) {
            vendor.rename(request.name());
        }
        if (request.homepageUrl() != null) {
            // 빈 문자열은 "지우기" 입니다. null 은 여기까지 오지 않습니다 — 그건 "그대로 두기" 입니다.
            vendor.changeHomepageUrl(request.homepageUrl());
        }

        String warning = null;
        if (request.isActive() != null && request.isActive() != vendor.isActive()) {
            vendor.changeActive(request.isActive());
            warning = request.isActive() ? null : deactivationWarning(vendor);
        }

        return AdminVendorResponse.of(vendor, warning);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * D7 규약의 2단계를 잊지 않게 합니다.
     *
     * <p>제공처만 등록하고 크롤러 시드를 안 고치면 아무 일도 일어나지 않습니다.
     * 오류도 로그도 없어서, 며칠 뒤 "그 학과 공지가 왜 안 올라오지" 로 발견됩니다.
     */
    private static String seedReminder(Vendor vendor) {
        return "크롤러 시드에 \"vendor\": \"" + vendor.getInitial() + "\" 를 추가해야 수집이 시작됩니다.";
    }

    /**
     * <b>비활성화는 수집을 멈추지 않습니다.</b>
     *
     * <p>{@code is_active} 는 목록·필터 노출만 가립니다. 크롤러는 시드를 보고 움직이고
     * {@code article_vendors} 에는 이 플래그를 보는 제약이 없어서,
     * 비활성 제공처의 공지가 계속 들어옵니다. 화면에서만 사라진 상태라 더 헷갈립니다.
     */
    private static String deactivationWarning(Vendor vendor) {
        return "목록·필터에서만 숨겨집니다. 수집을 멈추려면 크롤러 시드에서 \""
                + vendor.getInitial() + "\" 를 함께 빼야 합니다.";
    }

    private static void rejectImmutableChange(String label, Object current, Object requested, String why) {
        if (requested != null && !requested.equals(current)) {
            throw new BusinessException(
                    ErrorCode.IMMUTABLE_FIELD,
                    label + " — 등록 후 바꿀 수 없습니다. " + why
                            + " 값을 바꿔야 한다면 제공처를 새로 등록하고 기존 것은 비활성화하세요.");
        }
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Vendor load(Long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENDOR_NOT_FOUND));
    }
}
