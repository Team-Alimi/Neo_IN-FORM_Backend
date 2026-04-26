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
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.UserRole;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.UserRepository;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AdminAuthorizationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private today.inform.inform_backend.repository.VendorRepository vendorRepository;

    @Autowired
    private today.inform.inform_backend.repository.ClubArticleRepository clubArticleRepository;

    @Autowired
    private today.inform.inform_backend.repository.SchoolArticleRepository schoolArticleRepository;

    @Autowired
    private today.inform.inform_backend.repository.BookmarkRepository bookmarkRepository;

    @Autowired
    private today.inform.inform_backend.repository.NotificationRepository notificationRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private Integer userId;
    private Integer adminId;
    private String userAccessToken;
    private String adminAccessToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        notificationRepository.deleteAll();
        bookmarkRepository.deleteAll();
        clubArticleRepository.deleteAll();
        schoolArticleRepository.deleteAll();
        userRepository.deleteAll();
        vendorRepository.deleteAll();

        // 일반 유저용 전공 생성
        String uniqueInitialUser = "USER_CSE" + System.currentTimeMillis();
        Vendor userMajor = vendorRepository.save(Vendor.builder()
                .vendorName("컴퓨터공학과")
                .vendorInitial(uniqueInitialUser)
                .vendorType(VendorType.SCHOOL)
                .build());

        // 일반 유저 생성
        String uniqueEmailUser = "user" + System.currentTimeMillis() + "@inha.edu";
        User user = userRepository.save(User.builder()
                .email(uniqueEmailUser)
                .name("일반유저")
                .major(userMajor)
                .role(UserRole.ROLE_USER)
                .build());
        this.userId = user.getUserId();

        // 관리자용 전공 생성
        String uniqueInitialAdmin = "ADMIN_CSE" + System.currentTimeMillis();
        Vendor adminMajor = vendorRepository.save(Vendor.builder()
                .vendorName("관리자학과")
                .vendorInitial(uniqueInitialAdmin)
                .vendorType(VendorType.SCHOOL)
                .build());

        // 관리자 생성
        String uniqueEmailAdmin = "admin" + System.currentTimeMillis() + "@inha.edu";
        User admin = userRepository.save(User.builder()
                .email(uniqueEmailAdmin)
                .name("관리자")
                .major(adminMajor)
                .role(UserRole.ROLE_ADMIN)
                .build());
        this.adminId = admin.getUserId();

        // 토큰 생성
        this.userAccessToken = jwtProvider.createAccessToken(userId, uniqueEmailUser, "ROLE_USER");
        this.adminAccessToken = jwtProvider.createAccessToken(adminId, uniqueEmailAdmin, "ROLE_ADMIN");
    }

    @Test
    @DisplayName("ROLE_USER 권한으로 관리자 API 호출 시 403 에러가 발생한다.")
    void accessAdminApi_WithUserRole_Fail() throws Exception {
        // given

        // when & then
        mockMvc.perform(get("/api/v1/admin/check")
                        .header("Authorization", "Bearer " + userAccessToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN 권한으로 관리자 API 호출 시 성공한다.")
    void accessAdminApi_WithAdminRole_Success() throws Exception {
        // given
        String accessToken = jwtProvider.createAccessToken(adminId, "admin@inha.edu", "ROLE_ADMIN");

        // when & then
        mockMvc.perform(get("/api/v1/admin/check")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_ADMIN 권한은 일반 유저 보호 API도 호출 가능하다.")
    void accessUserApi_WithAdminRole_Success() throws Exception {
        // given
        String accessToken = jwtProvider.createAccessToken(adminId, "admin@inha.edu", "ROLE_ADMIN");

        // when & then (본인 정보 조회 등 hasAnyRole("USER", "ADMIN") 적용된 경로)
        mockMvc.perform(get("/api/v1/users/me") // SecurityConfig에서 hasAnyRole("USER", "ADMIN") 확인 필요
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isOk());
    }
}
