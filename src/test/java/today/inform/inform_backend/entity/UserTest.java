package today.inform.inform_backend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("유저 생성 시 알림 리스트가 빈 상태로 초기화된다.")
    void user_NotificationList_Initialization() {
        User user = User.builder()
                .email("test@inha.edu")
                .name("테스터")
                .build();

        assertThat(user.getNotifications()).isNotNull();
        assertThat(user.getNotifications()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test@inha.edu", "test@inha.ac.kr"})
    @DisplayName("인하대 도메인 이메일은 검증을 통과한다.")
    void validateInhaDomain_Success(String email) {
        assertThatCode(() -> User.validateInhaDomain(email))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test@gmail.com", "test@naver.com", "test@inha.com"})
    @DisplayName("인하대 도메인이 아닌 이메일은 예외가 발생한다.")
    void validateInhaDomain_Fail(String email) {
        assertThatThrownBy(() -> User.validateInhaDomain(email))
                .isInstanceOf(today.inform.inform_backend.common.exception.BusinessException.class);
    }
}
