package today.inform.inform_backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import today.inform.inform_backend.config.jwt.JwtProvider;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JwtFilterIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private today.inform.inform_backend.repository.UserRepository userRepository;

    @Autowired
    private today.inform.inform_backend.repository.VendorRepository vendorRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
        vendorRepository.deleteAll();

        // 테스트 데이터 준비
        today.inform.inform_backend.entity.Vendor major = vendorRepository.save(today.inform.inform_backend.entity.Vendor.builder()
                .vendorName("컴퓨터공학과")
                .vendorInitial("CSE")
                .vendorType(today.inform.inform_backend.entity.VendorType.SCHOOL)
                .build());

        today.inform.inform_backend.entity.User user = userRepository.save(today.inform.inform_backend.entity.User.builder()
                .email("test@inha.edu")
                .name("테스터")
                .major(major) // not null 제약이 있다면 major를 넣어줘야 할 수도 있음 (현재 nullable이지만 안정성을 위해)
                .build());

        this.testUserId = user.getUserId();
        this.testMajorId = major.getVendorId();
    }

    private Integer testUserId;
    private Integer testMajorId;

    @Test
    @DisplayName("유효한 토큰으로 인증이 필요한 API 호출 시 성공한다.")
    void access_WithValidToken_Success() throws Exception {
        // given
        String accessToken = jwtProvider.createAccessToken(testUserId, "test@inha.edu");

        // when & then
        mockMvc.perform(patch("/api/v1/users/" + testUserId + "/major")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"majorId\": " + testMajorId + "}"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없이 인증이 필요한 API 호출 시 403 에러가 발생한다.")
    void access_WithoutToken_Fail() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/users/" + testUserId + "/major")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"majorId\": " + testMajorId + "}"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("타인의 ID로 전공 변경을 시도하면 400 에러가 발생한다.")
    void updateMajor_OtherUser_Fail() throws Exception {
        // given
        String accessToken = jwtProvider.createAccessToken(testUserId, "test@inha.edu");
        Integer otherUserId = testUserId + 999;

        // when & then
        mockMvc.perform(patch("/api/v1/users/" + otherUserId + "/major")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"majorId\": " + testMajorId + "}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
