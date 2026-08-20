package today.inform.inform.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 Access Token 을 SecurityContext 에 채웁니다.
 *
 * <p>토큰이 없거나 잘못돼도 <b>여기서 막지 않습니다.</b> 인증 없이 통과시키고
 * 접근 판단은 {@code SecurityConfig} 의 경로 규칙에 맡깁니다.
 * 공개 엔드포인트(캘린더·서비스 공지)가 토큰 없이도 동작해야 하기 때문입니다.
 *
 * <p>principal 로 {@link AuthPrincipal} 을 넣습니다.
 * 감사 행위자 주입({@code AuditAwareTransactionManager})이 이 타입을 읽습니다.
 *
 * <p>여기서 요청마다 Redis 를 한 번 읽습니다({@link TokenRevocationStore}).
 * 그 비용을 내는 이유는 stateless 토큰을 되돌릴 다른 방법이 없기 때문입니다 —
 * 탈퇴한 사용자가 남은 토큰으로 1시간 동안 계속 글을 쓸 수 있으면 탈퇴가 탈퇴가 아닙니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRevocationStore revocationStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            AccessToken accessToken = jwtTokenProvider.parseAccessTokenOrNull(token);

            // ★ 서명이 유효해도 무효화된 토큰이면 인증하지 않습니다.
            //   탈퇴·전체 로그아웃 이후 남은 토큰이 만료까지 통하는 걸 막습니다.
            if (accessToken != null
                    && !revocationStore.isRevoked(accessToken.principal().userId(), accessToken.issuedAt())) {
                AuthPrincipal principal = accessToken.principal();
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(principal.role().authority())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
