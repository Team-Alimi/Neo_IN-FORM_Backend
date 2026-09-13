package today.inform.inform.global.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.JwtAuthenticationFilter;

/**
 * 접근 권한 (POLICY 22장).
 *
 * <pre>
 * Guest 가능 :  캘린더 · 인기 공지 · 서비스 공지 · 제공처/카테고리 목록
 *               로그인 · 토큰 재발급
 * 로그인 필요:  공지 목록 · 상세 · 검색 · 댓글 열람 · 그 외 전부
 * 관리자     :  /admin 이하
 * </pre>
 *
 * <p><b>★ {@code /articles/**} 로 뭉뚱그리면 안 됩니다.</b>
 * 캘린더는 열고 공지 목록은 닫아야 하므로 경로를 개별 등록합니다.
 * Spring Security 는 등록 순서대로 평가하므로 <b>구체적인 패턴이 먼저</b> 와야 합니다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String P = WebConfig.API_PREFIX;

    /**
     * CORS 허용 출처. 쉼표로 여러 개.
     *
     * <p>기본값에 로컬 개발 주소를 넣어 둔 이유는, 프론트가 별도 설정 없이 바로 붙을 수 있어야
     * 하기 때문입니다. 운영에서 좁히려면 {@code CORS_ALLOWED_ORIGINS} 로 덮어쓰면 됩니다.
     *
     * <p><b>와일드카드를 쓰지 않습니다.</b> 지금은 자격증명을 안 보내서 {@code *} 도 동작하지만,
     * 나중에 쿠키를 쓰기 시작하는 순간 브라우저가 거부하고 원인을 찾기 어려워집니다.
     */
    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT 기반 stateless API 이므로 세션과 CSRF 토큰을 쓰지 않습니다.
                // ★ CORS 를 여기서 켜야 preflight 가 인증보다 먼저 처리됩니다.
                //   안 켜면 브라우저의 OPTIONS 요청이 인증 필터까지 가서 401 을 받습니다.
                //   OPTIONS 에는 Authorization 헤더가 안 붙으므로 절대 통과할 수 없고,
                //   프론트는 "CORS 차단" 으로만 보게 됩니다 — 원인이 인증이라는 게 안 보입니다.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // ── 인증 진입점 ─────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                P + "/auth/login/google",
                                P + "/auth/token/refresh").permitAll()

                        // ── Guest 허용 ──────────────────────────────────────
                        // 캘린더는 열지만 여기서 상세로 넘어가면 로그인이 필요합니다.
                        .requestMatchers(HttpMethod.GET,
                                P + "/calendar/**",
                                // 홈 화면이 캘린더이고, 그 상단의 인기 목록도 같은 화면에 그려집니다.
                                // 캘린더만 열고 이것을 닫으면 비로그인 홈이 반쪽만 나옵니다.
                                // 상세로 넘어가는 순간은 여전히 로그인이 필요합니다.
                                P + "/articles/popular",
                                P + "/announcements",
                                P + "/announcements/*",
                                P + "/vendors",
                                P + "/categories",
                                P + "/club-types").permitAll()

                        // ── 인프라 ──────────────────────────────────────────
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // ── 관리자 ──────────────────────────────────────────
                        .requestMatchers(P + "/admin/**").hasRole("ADMIN")

                        // ── 나머지 전부 로그인 필요 ──────────────────────────
                        // 공지 목록·상세·검색·댓글 열람이 여기 해당합니다.
                        .anyRequest().authenticated())

                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 정책.
     *
     * <p><b>{@code allowCredentials} 를 켜지 않습니다.</b> 이 API 는 토큰을
     * {@code Authorization} 헤더로 받습니다 — 쿠키를 안 쓰므로 켤 이유가 없고,
     * 켜는 순간 출처 와일드카드가 금지되고 CSRF 고려가 다시 따라붙습니다.
     *
     * <p>{@code maxAge} 는 preflight 결과를 브라우저가 캐시하는 시간입니다.
     * 없으면 요청마다 OPTIONS 가 한 번씩 더 나갑니다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        // 첨부 업로드 응답의 Location 을 프론트가 읽어야 합니다.
        config.setExposedHeaders(List.of("Location", "Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 인증 실패(401)도 {@code ApiResponse} 형태로 내보냅니다.
     * 기본 동작은 빈 본문이라 클라이언트가 다른 에러와 같은 방식으로 처리할 수 없습니다.
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
    }

    /** 권한 부족(403). 로그인은 했으나 관리자가 아닌 경우입니다. */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
    }

    /**
     * 인증/인가 실패 본문을 직접 씁니다.
     *
     * <p>ObjectMapper 를 주입하지 않는 이유 — 이 시점은 Security 필터 체인이고,
     * 본문 모양이 고정이라 직렬화기가 필요 없습니다. 의존을 줄이면
     * Jackson 구성이 바뀌어도 인증 실패 응답은 영향을 받지 않습니다.
     */
    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\""
                        + escape(code) + "\",\"message\":\"" + escape(message) + "\"}}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
