package today.inform.inform_backend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("학교 이메일(@inha.edu/@inha.ac.kr)로만 로그인할 수 있습니다.");
    }
}
