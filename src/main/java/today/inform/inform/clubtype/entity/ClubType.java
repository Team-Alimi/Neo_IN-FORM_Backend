package today.inform.inform.clubtype.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import today.inform.inform.global.entity.BaseTimeEntity;

/**
 * 동아리 추천 taxonomy. 온보딩에서 사용자가 고르고, {@code vendor_club_types} 로 동아리에 태깅됩니다.
 * 추천 점수는 사용자와 동아리가 공유하는 active 유형 수입니다.
 *
 * <p><b>읽기 전용입니다.</b> 테이블 주석이 "항목은 seed 데이터로 관리" 라고 못박고 있어
 * {@link today.inform.inform.category.entity.Category} 와 달리 등록·수정 메서드를 두지 않았습니다.
 * 목록을 바꾸려면 마이그레이션으로 합니다(V13 참조).
 *
 * <p>{@code code} 는 화면에 나가지 않습니다. {@code name} 은 표시 전용이라 자유롭게 바꿉니다.
 */
@Getter
@Entity
@Table(name = "club_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** false = 신규 선택에서 숨김. 이미 고른 사용자의 관심은 보존됩니다(IN008). */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
