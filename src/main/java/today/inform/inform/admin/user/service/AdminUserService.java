package today.inform.inform.admin.user.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.user.dto.response.AdminUserSummary;
import today.inform.inform.admin.user.repository.AdminUserRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.support.LikePattern;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;
import today.inform.inform.user.entity.UserStatus;

/**
 * ADM-16 회원 역할 관리.
 *
 * <p>v1 에서는 DB 를 직접 고치는 것이 유일한 방법이었습니다. 그래서 <b>누가 언제 누구에게
 * 관리자 권한을 줬는지 아무 기록이 없었습니다.</b> 이 기능의 존재 이유가 그 기록입니다 —
 * 권한을 바꾸는 것 자체는 UPDATE 한 줄이면 됩니다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;

    /**
     * 회원 목록.
     *
     * @param keyword 이메일·이름 부분 일치. 2글자 미만이면 무시합니다 —
     *                한 글자로는 전체와 다를 바 없어 목록만 무거워집니다
     */
    @Transactional(readOnly = true)
    public Page<AdminUserSummary> search(String keyword, UserRole role, UserStatus status,
                                         Pageable pageable) {
        return adminUserRepository
                .search(likeKeyword(keyword), role, status, dropSort(pageable))
                .map(AdminUserSummary::from);
    }

    /**
     * ADM-16 권한 변경.
     *
     * @return 실제로 바뀌었으면 {@code true}. 같은 권한이면 아무것도 하지 않고 {@code false} 입니다 —
     *         호출자가 이 값을 보고 토큰 무효화 여부를 정합니다
     */
    @Transactional
    public boolean changeRole(Long targetUserId, UserRole newRole, Long actorId) {
        requireNotSelf(targetUserId, actorId);

        User target = adminUserRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (target.getRole() == newRole) {
            return false;
        }
        requirePromotable(target, newRole);

        target.changeRole(newRole);
        return true;
    }

    @Transactional(readOnly = true)
    public AdminUserSummary get(Long userId) {
        return adminUserRepository.findById(userId)
                .map(AdminUserSummary::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 자기 자신의 권한은 바꿀 수 없습니다.
     *
     * <p><b>마지막 관리자가 사라지는 것을 막는 장치입니다.</b> 관리자는 서로를 강등할 수 있지만
     * 자기 자신은 못 하므로, 어떤 순서로 강등해도 <b>최소 한 명은 남습니다.</b>
     * "관리자 수가 1명이면 거부" 같은 조건은 필요 없고, 그렇게 세면 두 관리자가 동시에
     * 서로를 강등할 때 둘 다 통과해 아무도 안 남을 수 있습니다.
     *
     * <p>관리자가 정말 그만두려면 다른 관리자에게 부탁해야 합니다. 그 한 단계가
     * "관리자 0명" 이라는 되돌리기 어려운 상태를 막습니다 —
     * 그 상태가 되면 이 API 를 쓸 수 있는 사람이 없어져 DB 를 직접 고쳐야 합니다.
     */
    private static void requireNotSelf(Long targetUserId, Long actorId) {
        if (targetUserId.equals(actorId)) {
            throw new BusinessException(
                    ErrorCode.CANNOT_CHANGE_OWN_ROLE,
                    "자신의 권한은 변경할 수 없습니다. 관리자가 한 명도 남지 않는 것을 막기 위한 제한입니다.");
        }
    }

    /**
     * 탈퇴한 계정은 관리자로 올릴 수 없습니다.
     *
     * <p>반대로 <b>강등은 허용합니다.</b> 관리자가 탈퇴하면 계정만 WITHDRAWN 이 되고
     * {@code role} 은 ADMIN 인 채로 남습니다. 그 계정을 정리할 방법이 없으면
     * 회원 목록에 "탈퇴한 관리자" 가 영원히 남습니다.
     */
    private static void requirePromotable(User target, UserRole newRole) {
        if (newRole == UserRole.ADMIN && target.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 회원에게는 관리자 권한을 줄 수 없습니다.");
        }
    }

    /**
     * 이메일을 소문자로 낮춰 비교합니다.
     *
     * <p>{@code users.email} 은 CHECK 로 소문자만 저장되지만 {@code name} 은 아닙니다.
     * 쿼리가 양쪽 다 {@code lower()} 로 감싸므로 검색어도 낮춰야 대소문자 무시가 성립합니다.
     */
    private static String likeKeyword(String keyword) {
        if (keyword == null || keyword.trim().length() < 2) {
            return null;
        }
        return LikePattern.contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    /** 정렬은 가입 최신순 고정입니다. 이유는 {@code AdminUserRepository#search} 주석 참조. */
    private static Pageable dropSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
