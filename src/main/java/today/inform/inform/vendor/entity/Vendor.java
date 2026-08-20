package today.inform.inform.vendor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.entity.BaseTimeEntity;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 공지 제공처 — 학과·기관(SCHOOL) 또는 동아리(CLUB).
 *
 * <p><b>{@code initial} 과 {@code type} 은 생성 후 바꿀 수 없습니다.</b>
 * 트리거 {@code trg_vendors_10_immutable} 이 IN001 로 막습니다.
 * 그래서 두 컬럼을 {@code updatable = false} 로 매핑합니다 —
 * 이렇게 두면 어떤 경로로 값을 바꿔도 UPDATE 문에 실리지 않아 트리거가 발동할 일 자체가 없습니다.
 * <b>거부는 서비스가 먼저 합니다.</b> JPA 가 조용히 무시하게만 두면
 * 관리자는 200 을 받고 값이 바뀐 줄 알게 됩니다.
 *
 * <p><b>왜 {@code initial} 이 불변인가</b>
 * 크롤러 시드가 제공처를 이름이 아니라 이 값으로 찾습니다(D7 규약).
 * 여기서 바꾸면 다음 수집부터 <b>제공처를 찾지 못해 그 학과 공지가 통째로 안 들어옵니다.</b>
 * 오류가 아니라 "새 공지가 없는 것처럼 보이는" 조용한 정지라 알아채기 어렵습니다.
 *
 * <p><b>왜 {@code type} 이 불변인가</b>
 * {@code article_vendors} 의 교차 검증({@code trg_av_10_integrity}, IN002)이
 * "공지의 출처 유형 = 제공처 유형" 을 전제합니다. 뒤늦게 바꾸면 이미 붙은 관계가 소급으로 깨집니다.
 *
 * <p>삭제는 없습니다. {@code article_vendors} 가 {@code ON DELETE RESTRICT} 라
 * 공지가 하나라도 붙어 있으면 DB 가 거부합니다. 대신 {@link #deactivate()} 로 숨깁니다.
 */
@Getter
@Entity
@Table(name = "vendors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vendor extends BaseTimeEntity {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int INITIAL_MAX_LENGTH = 100;
    public static final int HOMEPAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    /** 크롤러 시드가 참조하는 business key. 생성 후 변경 불가(IN001). */
    @Column(name = "initial", nullable = false, length = INITIAL_MAX_LENGTH, updatable = false)
    private String initial;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private SourceType type;

    @Column(name = "homepage_url", length = HOMEPAGE_URL_MAX_LENGTH)
    private String homepageUrl;

    /** false = 신규 목록·필터에서 숨김. 이미 붙은 공지의 출처 표시는 그대로 유지됩니다. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    private Vendor(String name, String initial, SourceType type, String homepageUrl) {
        this.name = requireName(name);
        this.initial = requireInitial(initial);
        this.type = requireType(type);
        this.homepageUrl = normalizeUrl(homepageUrl);
        this.active = true;
    }

    /** VND-01 등록. 크롤러 시드에 {@code initial} 을 추가하기 <b>전에</b> 여기가 먼저입니다(D7 1단계). */
    public static Vendor create(String name, String initial, SourceType type, String homepageUrl) {
        return new Vendor(name, initial, type, homepageUrl);
    }

    /** VND-02 이름 변경. 표시 전용이라 자유롭게 바꿀 수 있습니다. */
    public void rename(String newName) {
        this.name = requireName(newName);
    }

    /**
     * VND-02 홈페이지 변경.
     *
     * <p>{@code null} 은 "바꾸지 않음" 이라 여기까지 오지 않습니다. 지우려면 빈 문자열을 보냅니다 —
     * 서비스가 그것을 {@code null} 로 바꿔 넘깁니다.
     */
    public void changeHomepageUrl(String newUrl) {
        this.homepageUrl = normalizeUrl(newUrl);
    }

    /** VND-03. 기존 관계는 건드리지 않고 신규 노출에서만 뺍니다. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changeActive(boolean value) {
        this.active = value;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String requireName(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "제공처 이름을 입력해 주세요.");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "제공처 이름은 " + NAME_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    /**
     * 앞뒤 공백을 반드시 걷어냅니다.
     *
     * <p>크롤러는 시드의 문자열과 <b>정확히</b> 일치하는 제공처를 찾습니다.
     * {@code "반도체 "} 처럼 공백 하나가 섞여 들어가면 화면에서는 구분되지 않는데
     * 수집만 안 되는, 원인을 찾기 매우 어려운 상태가 됩니다.
     * 게다가 불변이라 나중에 고칠 수도 없어 제공처를 새로 만들어야 합니다.
     */
    private static String requireInitial(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "크롤러 식별자(initial)를 입력해 주세요.");
        }
        if (trimmed.length() > INITIAL_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "크롤러 식별자는 " + INITIAL_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "크롤러 식별자에는 공백을 넣을 수 없습니다. 시드 문자열과 정확히 일치해야 합니다.");
        }
        return trimmed;
    }

    private static SourceType requireType(SourceType value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "제공처 유형을 선택해 주세요.");
        }
        return value;
    }

    private static String normalizeUrl(String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > HOMEPAGE_URL_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "홈페이지 주소는 " + HOMEPAGE_URL_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
