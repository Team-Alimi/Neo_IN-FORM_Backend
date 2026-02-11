package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.repository.UserRepository;
import today.inform.inform_backend.repository.VendorRepository;
import today.inform.inform_backend.service.user.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VendorRepository vendorRepository;

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
    @DisplayName("존재하지 않는 사용자일 경우 예외가 발생한다.")
    void updateMajor_UserNotFound() {
        // given
        given(userRepository.findById(any())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateMajor(1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 학과일 경우 예외가 발생한다.")
    void updateMajor_MajorNotFound() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(vendorRepository.findById(any())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateMajor(userId, 999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 학과입니다.");
    }
}
