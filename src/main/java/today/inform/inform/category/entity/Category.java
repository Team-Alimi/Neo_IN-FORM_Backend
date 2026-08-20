package today.inform.inform.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import today.inform.inform.global.entity.BaseTimeEntity;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 공지 분류. 크롤러의 AI 분류 결과가 이 목록의 {@code code} 로 들어옵니다.
 *
 * <p><b>{@code code} 는 생성 후 바꿀 수 없습니다</b>(트리거 {@code trg_categories_10_immutable}, IN001).
 * 크롤러가 분류 결과를 이 문자열로 보내기 때문입니다.
 * 바꾸면 다음 수집분이 <b>분류 없이 쌓이고</b>, 관리자 목록에서 "확인 필요" 로만 보입니다.
 *
 * <p>반대로 {@code name} 과 {@code sortOrder} 는 화면 표시 전용이라 자유롭게 바꿉니다.
 * 연동 계약과 무관합니다.
 *
 * <p><b>삭제와 비활성화는 쓰임이 다릅니다.</b>
 * <ul>
 *   <li>삭제(CAT-03) — 잘못 만든 분류를 없던 일로. <b>아무도 안 쓰는 경우에만</b> 가능합니다
 *       ({@code article_categories} · {@code user_interest_categories} 가 RESTRICT).</li>
 *   <li>비활성화 — 쓰이던 분류를 접을 때. 기존 관계는 그대로 두고 신규 선택에서만 뺍니다.
 *       실제로 운영에 들어간 분류는 사실상 이쪽밖에 없습니다.</li>
 * </ul>
 */
@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    public static final int CODE_MAX_LENGTH = 50;
    public static final int NAME_MAX_LENGTH = 100;

    /** 크롤러와 주고받는 계약 키라 모양을 좁게 고정합니다. */
    private static final String CODE_PATTERN = "[A-Z0-9_]+";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 크롤러 AI 분류 계약 키. 생성 후 변경 불가(IN001). */
    @Column(name = "code", nullable = false, length = CODE_MAX_LENGTH, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    /** false = 신규 선택·분류에서 숨김. 이미 붙은 관계는 보존됩니다. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private Category(String code, String name, int sortOrder) {
        this.code = requireCode(code);
        this.name = requireName(name);
        this.sortOrder = sortOrder;
        this.active = true;
    }

    /** CAT-01 등록. {@code code} 는 크롤러 AI 분류 목록과 <b>먼저 맞춰 두고</b> 넣어야 합니다. */
    public static Category create(String code, String name, int sortOrder) {
        return new Category(code, name, sortOrder);
    }

    /** CAT-02. 표시 전용이라 자유입니다. */
    public void rename(String newName) {
        this.name = requireName(newName);
    }

    public void changeSortOrder(int newSortOrder) {
        this.sortOrder = newSortOrder;
    }

    public void changeActive(boolean value) {
        this.active = value;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 대문자로 고정합니다.
     *
     * <p>크롤러가 보내는 값과 대소문자만 다르면 UNIQUE 에도 안 걸리고 분류만 조용히 실패합니다.
     * 목록에서는 {@code SCHOLARSHIP} 과 {@code Scholarship} 이 둘 다 그럴듯해 보여서
     * 어느 쪽이 진짜인지 화면만 봐서는 알 수 없습니다. 한 가지 모양으로만 저장합니다.
     *
     * <p>{@code Locale.ROOT} 를 쓰는 이유는 터키어 로케일에서 소문자 i 가 점 있는 대문자로
     * 바뀌는 문제를 피하기 위함입니다.
     */
    private static String requireCode(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "분류 코드를 입력해 주세요.");
        }
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "분류 코드는 " + CODE_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        if (!normalized.matches(CODE_PATTERN)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "분류 코드는 영문 대문자·숫자·밑줄만 쓸 수 있습니다. 크롤러와 주고받는 계약 키입니다.");
        }
        return normalized;
    }

    private static String requireName(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "분류 이름을 입력해 주세요.");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "분류 이름은 " + NAME_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }
}
