package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.vendor.dto.request.CreateVendorRequest;
import today.inform.inform.admin.vendor.dto.request.UpdateVendorRequest;
import today.inform.inform.admin.vendor.dto.response.AdminVendorResponse;
import today.inform.inform.admin.vendor.service.AdminVendorService;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.support.IntegrationTest;
import today.inform.inform.vendor.entity.Vendor;
import today.inform.inform.vendor.repository.VendorRepository;

/**
 * VND-01 ~ VND-03 제공처 관리.
 *
 * <p><b>이 도메인에서 잘못되면 오류가 아니라 "공지가 안 들어옴" 으로 나타납니다.</b>
 * 크롤러가 시드의 문자열로 제공처를 찾기 때문에 {@code initial} 하나가 어긋나면
 * 그 학과 공지가 통째로 조용히 빠집니다. 그래서 그 값을 지키는 검사에 무게를 둡니다.
 */
@Transactional
class AdminVendorTest extends IntegrationTest {

    @Autowired
    private AdminVendorService vendorService;

    @Autowired
    private VendorRepository vendorRepository;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // VND-01 등록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("제공처를 등록하면 크롤러 시드 등록 안내가 함께 나온다")
    void createReturnsSeedReminder() {
        // ★ 이름과 식별자를 겹치지 않게 잡습니다. 이름이 식별자를 포함하면
        //   안내문에 이름이 들어가도 이 검사가 통과해, 정작 지켜야 할 값을 못 지킵니다.
        AdminVendorResponse created = create("반도체시스템공학과", "SEMICON", SourceType.SCHOOL);

        assertThat(created.id()).isNotNull();
        assertThat(created.isActive()).isTrue();
        assertThat(created.warning())
                .as("제공처만 등록하고 시드를 안 고치면 아무 일도 일어나지 않습니다 — 오류도 로그도 없습니다")
                .contains("SEMICON")
                .doesNotContain("반도체시스템공학과");
    }

    @Test
    @DisplayName("★ 크롤러 식별자 앞뒤 공백은 저장 전에 걷어낸다")
    void initialIsTrimmed() {
        AdminVendorResponse created = create("공백학과", "  공백테스트  ", SourceType.SCHOOL);

        assertThat(created.initial())
                .as("화면에서는 구분되지 않는데 수집만 안 되고, 불변이라 나중에 고칠 수도 없습니다")
                .isEqualTo("공백테스트");
    }

    @Test
    @DisplayName("크롤러 식별자 가운데 공백은 거부한다")
    void initialWithInnerWhitespaceIsRejected() {
        assertThatThrownBy(() -> create("이상한학과", "공백 낀식별자", SourceType.SCHOOL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("크롤러 식별자가 겹치면 어느 값이 문제인지 알려 준다")
    void duplicateInitialIsRejectedWithReason() {
        create("먼저학과", "중복키", SourceType.SCHOOL);
        em.flush();

        assertThatThrownBy(() -> create("나중학과", "중복키", SourceType.SCHOOL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("중복키");
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 식별자도 중복으로 잡는다")
    void duplicateIsDetectedAfterNormalization() {
        create("먼저학과", "정규화키", SourceType.SCHOOL);
        em.flush();

        assertThatThrownBy(() -> create("나중학과", "  정규화키 ", SourceType.SCHOOL))
                .as("요청 원문으로 검사하면 여기를 통과한 뒤 UNIQUE 에서 터집니다")
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VND-02 수정 — 불변 컬럼
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 크롤러 식별자를 바꾸려 하면 거부한다 — 조용히 무시하면 바뀐 줄 안다")
    void initialCannotBeChanged() {
        Long vendorId = create("원래학과", "원래키", SourceType.SCHOOL).id();
        em.flush();

        assertThatThrownBy(() -> vendorService.update(vendorId,
                new UpdateVendorRequest(null, null, null, "새로운키", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMMUTABLE_FIELD);
    }

    @Test
    @DisplayName("★ 같은 식별자를 그대로 되돌려 보내면 통과한다 — 폼 전체를 보내는 화면이 막히면 안 된다")
    void unchangedInitialIsAccepted() {
        Long vendorId = create("폼학과", "폼키", SourceType.SCHOOL).id();
        em.flush();

        AdminVendorResponse updated = vendorService.update(vendorId,
                new UpdateVendorRequest("이름만 바꿈", null, null, "폼키", SourceType.SCHOOL));

        assertThat(updated.name()).isEqualTo("이름만 바꿈");
        assertThat(updated.initial()).isEqualTo("폼키");
    }

    @Test
    @DisplayName("제공처 유형도 바꿀 수 없다")
    void typeCannotBeChanged() {
        Long vendorId = create("유형학과", "유형키", SourceType.SCHOOL).id();
        em.flush();

        assertThatThrownBy(() -> vendorService.update(vendorId,
                new UpdateVendorRequest(null, null, null, null, SourceType.CLUB)))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VND-02 수정 — 부분 수정 규칙
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 보내지 않은 항목은 그대로 남는다 (DB 로 확인)")
    void omittedFieldsAreUntouched() {
        Long vendorId = create("유지학과", "유지키", SourceType.SCHOOL, "https://inha.ac.kr/keep").id();
        em.flush();

        vendorService.update(vendorId, new UpdateVendorRequest("새 이름", null, null, null, null));

        // ★ 응답 DTO 는 방금 만진 managed 엔티티에서 값을 꺼내므로, UPDATE 가 DB 에 가지 않아도
        //   그대로 통과합니다. flush + clear 후 DB 를 직접 읽어야 실제 반영을 확인할 수 있습니다.
        flushAndClear();
        assertThat(column(vendorId, "name")).isEqualTo("새 이름");
        assertThat(column(vendorId, "homepage_url"))
                .as("null 은 '그대로 두라' 는 뜻입니다")
                .isEqualTo("https://inha.ac.kr/keep");
    }

    @Test
    @DisplayName("★ 홈페이지 주소는 빈 문자열로 지운다 (DB 로 확인)")
    void emptyStringClearsHomepageUrl() {
        Long vendorId = create("지움학과", "지움키", SourceType.SCHOOL, "https://inha.ac.kr/drop").id();
        em.flush();

        vendorService.update(vendorId, new UpdateVendorRequest(null, "", null, null, null));

        flushAndClear();
        assertThat(column(vendorId, "homepage_url")).isNull();
    }

    @Test
    @DisplayName("★ 불변 컬럼은 UPDATE 문에 아예 실리지 않는다 — 매핑을 지우면 실패해야 한다")
    void immutableColumnsNeverReachTheDatabase() throws Exception {
        Long vendorId = create("불변학과", "불변키", SourceType.SCHOOL).id();
        flushAndClear();

        // ★ 서비스를 거치면 아무것도 검증되지 않습니다 — 거기엔 initial/type 을 세팅하는 코드가
        //   아예 없어서, updatable=false 가 없어도 같은 값이 다시 쓰일 뿐입니다.
        //   매핑이 하는 일은 "필드가 실제로 바뀌었을 때 UPDATE 에 안 싣는 것" 이므로
        //   필드를 강제로 바꿔 놓고 확인해야 구분이 생깁니다.
        Vendor managed = vendorRepository.findById(vendorId).orElseThrow();
        setField(managed, "initial", "바뀐키");
        setField(managed, "type", SourceType.CLUB);
        managed.rename("이름도 변경");

        flushAndClear();

        assertThat(column(vendorId, "initial"))
                .as("실리면 트리거가 IN001 로 막지만, 그건 최후 방어입니다. "
                        + "매핑이 먼저 걸러야 크롤러 시드가 어긋나는 경로 자체가 안 생깁니다")
                .isEqualTo("불변키");
        assertThat(column(vendorId, "type")).isEqualTo("SCHOOL");
        assertThat(column(vendorId, "name"))
                .as("같은 UPDATE 의 다른 컬럼은 정상 반영돼야 합니다")
                .isEqualTo("이름도 변경");
    }

    /** 엔티티의 불변 필드를 강제로 바꿉니다. 정상 경로에는 이런 수단이 없습니다 — 매핑 검증 전용입니다. */
    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VND-03 비활성화
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 비활성화해도 수집은 멈추지 않는다는 것을 응답이 알려 준다")
    void deactivationWarnsThatCrawlingContinues() {
        Long vendorId = create("접을학과", "접을키", SourceType.SCHOOL).id();
        em.flush();

        AdminVendorResponse updated = vendorService.update(vendorId,
                new UpdateVendorRequest(null, null, false, null, null));

        assertThat(updated.isActive()).isFalse();
        assertThat(updated.warning())
                .as("is_active 는 목록 노출만 가립니다. article_vendors 에는 이 플래그를 보는 제약이 없습니다")
                .contains("접을키");

        flushAndClear();
        assertThat(column(vendorId, "is_active")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("다시 켤 때는 경고가 붙지 않는다")
    void reactivationHasNoWarning() {
        Long vendorId = create("복귀학과", "복귀키", SourceType.SCHOOL).id();
        vendorService.update(vendorId, new UpdateVendorRequest(null, null, false, null, null));
        em.flush();

        AdminVendorResponse updated = vendorService.update(vendorId,
                new UpdateVendorRequest(null, null, true, null, null));

        assertThat(updated.isActive()).isTrue();
        assertThat(updated.warning()).isNull();
    }

    @Test
    @DisplayName("★ 관리 목록에는 비활성 제공처도 나온다 — 안 보이면 다시 켤 수 없다")
    void adminListIncludesInactiveVendors() {
        Long vendorId = create("숨은학과", "숨은키", SourceType.SCHOOL).id();
        vendorService.update(vendorId, new UpdateVendorRequest(null, null, false, null, null));
        em.flush();

        assertThat(vendorService.search(null, null))
                .extracting(AdminVendorResponse::id)
                .contains(vendorId);

        assertThat(vendorService.search(null, false))
                .extracting(AdminVendorResponse::id)
                .contains(vendorId);
        assertThat(vendorService.search(null, true))
                .extracting(AdminVendorResponse::id)
                .doesNotContain(vendorId);
    }

    @Test
    @DisplayName("없는 제공처를 수정하면 404")
    void updatingMissingVendorIsNotFound() {
        assertThatThrownBy(() -> vendorService.update(999_999_999L,
                new UpdateVendorRequest("아무개", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VENDOR_NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 영속성 컨텍스트 캐시가 아니라 DB 를 보게 만듭니다. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Object column(Long vendorId, String name) {
        return em.createNativeQuery("SELECT " + name + " FROM vendors WHERE id = :id")
                .setParameter("id", vendorId).getSingleResult();
    }

    private AdminVendorResponse create(String name, String initial, SourceType type) {
        return create(name, initial, type, null);
    }

    private AdminVendorResponse create(String name, String initial, SourceType type, String url) {
        return vendorService.create(new CreateVendorRequest(name, initial, type, url));
    }
}
