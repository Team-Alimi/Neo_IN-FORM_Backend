package today.inform.inform_backend.config.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "zqXW_v-Y0R2j8M5k7P3n9L2b1V4c6X8Z0A2S4D6F8G0H1J3K5L7M9N1B3V5C7X9",
                3600000,
                1209600000
        );
        jwtProvider = new JwtProvider(jwtProperties);
    }

    @Test
    @DisplayName("토큰 생성 및 검증 테스트")
    void createAndValidateToken() {
        String token = jwtProvider.createAccessToken(1, "test@inha.edu");
        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.getClaims(token).getSubject()).isEqualTo("1");
    }
}
