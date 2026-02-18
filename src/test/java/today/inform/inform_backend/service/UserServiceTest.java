package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.repository.*;
import today.inform.inform_backend.service.user.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VendorRepository vendorRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("사용자의 전공 정보를 성공적으로 업데이트한다.")
    void updateMajor_Success() {
        // given
        Integer userId = 1;
        Integer majorId = 10;
        User user = User.builder().userId(userId).email("test@inha.edu").name("홍길동").build();
        Vendor major = Vendor.builder().vendorId(majorId).vendorName("컴퓨터공학과").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(vendorRepository.findById(majorId)).willReturn(Optional.of(major));

        // when
        userService.updateMajor(userId, majorId);

        // then
        assertThat(user.getMajor()).isNotNull();
        assertThat(user.getMajor().getVendorName()).isEqualTo("컴퓨터공학과");
    }

    @Test
    @DisplayName("내 프로필 정보를 성공적으로 조회한다. (학과 설정됨)")
    void getMyProfile_WithMajor_Success() {
        // given
        Integer userId = 1;
        Vendor major = Vendor.builder().vendorId(10).vendorName("컴퓨터공학과").vendorInitial("CSE").vendorType(today.inform.inform_backend.entity.VendorType.SCHOOL).build();
        User user = User.builder()
                .userId(userId)
                .email("test@inha.edu")
                .name("홍길동")
                .major(major)
                .build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        today.inform.inform_backend.dto.LoginResponse.UserInfo result = userService.getMyProfile(userId);

        // then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@inha.edu");
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getMajor()).isNotNull();
        assertThat(result.getMajor().getVendorName()).isEqualTo("컴퓨터공학과");
    }

    @Test
    @DisplayName("회원 탈퇴 시 유저 정보와 연관 데이터, 토큰이 모두 삭제되어야 한다.")
    void withdraw_Success() {
        // given
        Integer userId = 1;
        User user = User.builder()
                .userId(userId)
                .email("user@inha.edu")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userService.withdraw(userId);

        // then
        verify(bookmarkRepository, times(1)).deleteAllByUser(user);
        verify(notificationRepository, times(1)).deleteAllByUser(user);
        verify(refreshTokenRepository, times(1)).deleteById(user.getEmail());
        verify(userRepository, times(1)).delete(user);
    }

    @Test
    @DisplayName("존재하지 않는 유저가 탈퇴 시도 시 USER_NOT_FOUND 예외가 발생한다.")
    void withdraw_Fail_UserNotFound() {
        // given
        Integer userId = 999;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
