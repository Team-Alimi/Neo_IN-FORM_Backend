package today.inform.inform.vendor.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.vendor.dto.response.VendorResponse;
import today.inform.inform.vendor.entity.Vendor;
import today.inform.inform.vendor.repository.VendorRepository;

/**
 * COM-01 제공처 목록 (사용자용).
 *
 * <p><b>활성 항목만 내보내는 것이 이 서비스의 존재 이유입니다.</b>
 * 관리자가 제공처를 비활성화하는 목적이 곧 "신규 선택지에서 빼기" 이므로
 * ({@code Vendor#deactivate}), 여기서 거르지 않으면 비활성화가 아무 효과도 없습니다.
 */
@Service
@RequiredArgsConstructor
public class VendorQueryService {

    /**
     * 한국어 이름 순서.
     *
     * <p><b>DB 의 ORDER BY 만으로는 부족합니다.</b> 이 DB 의 collation 은 {@code en_US.utf8} 이라
     * 한글에 대한 정렬 규칙이 없습니다. 실제로 "학생지원팀 → 컴퓨터공학과 → 정보통신공학과" 처럼
     * <b>사용자 눈에는 아무 순서도 아닌</b> 목록이 나옵니다.
     * 명세는 "이름 오름차순" 이라고만 적었지만, 그 말의 뜻은 한국어 화면에서 읽히는 순서입니다.
     *
     * <p>DB 에서 {@code COLLATE "ko-KR-x-icu"} 를 쓰는 방법도 있지만, 그건 그 collation 이
     * 설치된 서버에서만 동작합니다. 없는 곳에서는 <b>게스트 첫 화면이 통째로 오류</b>가 됩니다.
     * 목록이 많아야 수백 건이고 이미 전부 메모리에 올라와 있으므로, 앱에서 정렬해 그 위험을 없앱니다.
     *
     * <p>{@link Collator} 는 스레드 안전하지 않아 요청마다 새로 만듭니다 —
     * 필드로 공유하면 동시 요청에서 결과가 깨집니다.
     */
    private static final Comparator<Vendor> BY_KOREAN_NAME =
            Comparator.comparing(Vendor::getName, koreanCollator())
                    .thenComparing(Vendor::getId);

    private final VendorRepository vendorRepository;

    private static Comparator<String> koreanCollator() {
        return (left, right) -> Collator.getInstance(Locale.KOREAN).compare(left, right);
    }

    /**
     * @param type 생략하면 전부. 정렬은 <b>이름 오름차순</b>입니다(명세 4.7) —
     *             관리자 목록의 "유형 먼저" 정렬과 다르므로 쿼리를 따로 씁니다
     */
    @Transactional(readOnly = true)
    public List<VendorResponse> findActive(SourceType type) {
        // 활성 조건을 파라미터가 아니라 쿼리에 박아 둔 메서드를 씁니다.
        // 호출부가 넘기는 값으로 열어 두면 언젠가 비활성이 사용자 화면에 새어 나갑니다.
        List<Vendor> vendors = new ArrayList<>(vendorRepository.findActiveOrderByName(type));
        vendors.sort(BY_KOREAN_NAME);
        return VendorResponse.from(vendors);
    }
}
