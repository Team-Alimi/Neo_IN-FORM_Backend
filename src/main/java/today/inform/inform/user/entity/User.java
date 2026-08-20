package today.inform.inform.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import today.inform.inform.global.entity.BaseTimeEntity;

/**
 * 인하대 구성원. Google OAuth 로 가입합니다.
 *
 * <p><b>매핑 주의</b>
 * <ul>
 *   <li>{@code email} 은 소문자로만 저장합니다. DB CHECK({@code email = lower(email)})가 강제하므로
 *       정규화를 빠뜨리면 INSERT 자체가 실패합니다. 생성 경로를 {@link #create}로 단일화한 이유입니다.</li>
 *   <li>{@code role} 변경은 DB 트리거가 {@code user_role_logs} 에 자동 기록합니다.
 *       앱이 로그를 따로 쓰지 않습니다.</li>
 *   <li>구독 학과·기관은 {@code user_vendors} 관계 테이블입니다.
 *       단일 {@code major_vendor_id} 컬럼은 v11 에서 제거되었습니다(복수전공 지원).</li>
 * </ul>
 *
 * <p><b>왜 {@link Version} 과 {@link DynamicUpdate} 가 둘 다 필요한가</b>
 * 이 엔티티는 서로 다른 사람이 동시에 만지는 유일한 엔티티입니다 —
 * 본인이 설정·탈퇴를 바꾸는 동안 관리자가 권한을 바꿉니다(ADM-16).
 * 둘 다 없으면 나중에 커밋한 쪽이 <b>로드 시점 스냅샷으로 행 전체를 다시 써서</b>
 * 상대의 변경을 되돌립니다. 갱신 행 수가 1이라 예외도 나지 않습니다.
 *
 * <p>특히 나쁜 것은 감사 로그입니다. 되돌림 UPDATE 도 {@code role} 을 바꾸는 것이므로
 * {@code trg_users_90_role_audit} 이 반응해 {@code user_role_logs} 에
 * <b>"대상자 본인이 스스로 강등했다"</b> 는 없는 사실을 한 줄 남깁니다.
 * 반대 방향으로는 {@code status='ACTIVE'} 와 {@code withdrawn_at=NULL} 이 함께 되돌아가
 * 탈퇴한 계정이 되살아나는데, {@code ck_users_withdrawn} 은 두 값이 짝이 맞으므로 통과시킵니다.
 * (V10 마이그레이션. {@code articles}·{@code comments} 가 같은 이유로 이미 version 을 가집니다)
 */
@Getter
@Entity
@Table(name = "users")
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "email_notification_enabled", nullable = false)
    private boolean emailNotificationEnabled;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private User(String email, String name) {
        this.email = normalizeEmail(email);
        this.name = name;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.emailNotificationEnabled = true;
    }

    /** 신규 가입. AUTH-01 자동 가입에서만 호출합니다. */
    public static User create(String email, String name) {
        return new User(email, name);
    }

    /**
     * PostgreSQL 은 대소문자를 구분하므로 {@code Kim@inha.ac.kr} 과 {@code kim@inha.ac.kr} 이
     * 별개 계정이 됩니다. 활성 이메일 UNIQUE 인덱스도 이를 막지 못하기 때문에 저장 전에 낮춥니다.
     * {@code Locale.ROOT} 를 쓰는 이유는 터키어 로케일에서 {@code I → ı} 로 바뀌는 문제를 피하기 위함입니다.
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 온보딩을 아직 마치지 않았는지. 로그인 응답에 실어 프론트가 온보딩 화면으로 보냅니다. */
    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** 구글 프로필 이름이 바뀌면 반영합니다. 이메일은 계정 식별자이므로 변경하지 않습니다. */
    public void updateProfileName(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
    }

    public void changeEmailNotification(boolean enabled) {
        this.emailNotificationEnabled = enabled;
    }

    /**
     * ADM-16 권한 변경.
     *
     * <p><b>감사 기록은 여기서 남기지 않습니다.</b> DB 트리거
     * ({@code trg_users_90_role_audit})가 같은 트랜잭션에서 {@code user_role_logs} 에 씁니다.
     * "누가" 는 트랜잭션-로컬 GUC 로 전달되며, 그 주입은 {@code AuditAwareTransactionManager} 가
     * 쓰기 트랜잭션 진입 시점에 강제합니다. 앱이 로그를 빠뜨릴 경로 자체를 없앤 구조입니다.
     *
     * <p>같은 권한으로 다시 부르면 트리거가 {@code IS DISTINCT FROM} 으로 걸러 내므로
     * 이력이 늘어나지 않습니다.
     */
    public void changeRole(UserRole newRole) {
        this.role = newRole;
    }

    /** 이미 완료한 사용자가 다시 호출해도 최초 시각을 유지합니다. */
    public void completeOnboarding() {
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = OffsetDateTime.now();
        }
    }

    /**
     * soft delete.
     *
     * <p>DB CHECK 가 {@code (status='WITHDRAWN') = (withdrawn_at IS NOT NULL)} 을 강제하므로
     * 두 값을 반드시 함께 바꿔야 합니다. 하나만 바꾸면 UPDATE 가 거부됩니다.
     */
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = OffsetDateTime.now();
    }
}
