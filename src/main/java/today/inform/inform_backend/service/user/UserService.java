package today.inform.inform_backend.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.repository.*;

import today.inform.inform_backend.dto.LoginResponse;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public LoginResponse.UserInfo getMyProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return LoginResponse.UserInfo.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .major(user.getMajor() != null ? VendorListResponse.builder()
                        .vendorId(user.getMajor().getVendorId())
                        .vendorName(user.getMajor().getVendorName())
                        .vendorInitial(user.getMajor().getVendorInitial())
                        .vendorType(user.getMajor().getVendorType().name())
                        .build() : null)
                .build();
    }

    @Transactional
    public void updateMajor(Integer userId, Integer majorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Vendor major = vendorRepository.findById(majorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 학과입니다."));

        user.updateMajor(major);
    }

    @Transactional
    public void withdraw(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1. 연관 데이터 일괄 삭제 (최적화된 벌크 쿼리 사용)
        bookmarkRepository.deleteAllByUser(user);
        notificationRepository.deleteAllByUser(user);

        // 2. Redis 내 인증 토큰 삭제
        refreshTokenRepository.deleteById(user.getEmail());

        // 3. 유저 계정 삭제
        userRepository.delete(user);
    }
}
