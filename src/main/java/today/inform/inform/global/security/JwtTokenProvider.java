package today.inform.inform.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.user.entity.UserRole;

/**
 * JWT 발급·검증.
 *
 * <p><b>Access Token</b> — subject=userId, claim role. 1시간.
 * DB 조회 없이 인증을 끝내기 위해 role 을 토큰에 담습니다.
 * 권한이 바뀐 사용자는 토큰 만료 후 반영됩니다.
 *
 * <p><b>Refresh Token</b> — subject=userId, jti=tokenId. 14일.
 * <b>서명이 유효해도 그것만으로는 인증되지 않습니다.</b> Redis 에 해당 tokenId 가
 * 살아 있어야 유효합니다. 로그아웃·rotation·강제 무효화가 서버 측에서 가능해야 하기 때문입니다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity}") long refreshTokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(Long userId, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidityMs))
                .signWith(key)
                .compact();
    }

    /**
     * @param tokenId Redis 에 저장할 세션 식별자. 기기마다 다른 값이라 다중 로그인이 됩니다.
     */
    public String createRefreshToken(Long userId, String tokenId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(tokenId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenValidityMs))
                .signWith(key)
                .compact();
    }

    public static String newTokenId() {
        return UUID.randomUUID().toString();
    }

    public long refreshTokenValidityMs() {
        return refreshTokenValidityMs;
    }

    public long accessTokenValidityMs() {
        return accessTokenValidityMs;
    }

    /**
     * 서명과 만료를 검증하고 payload 를 돌려줍니다.
     *
     * @throws BusinessException 만료면 {@code TOKEN_EXPIRED}, 그 외 위조·형식 오류면 {@code INVALID_REFRESH_TOKEN}
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * 인증 필터용. 예외를 던지지 않고 실패 시 {@code null} 을 돌려줍니다.
     * 잘못된 토큰은 "인증 안 된 요청"으로 취급하고 Security 가 401 을 냅니다.
     *
     * <p>발급 시각을 함께 돌려주는 이유는 {@code TokenRevocationStore} 때문입니다 —
     * "이 시각 이전에 발급된 토큰은 무효" 를 판정하려면 {@code iat} 가 필요합니다.
     */
    public AccessToken parseAccessTokenOrNull(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            String role = claims.get(CLAIM_ROLE, String.class);
            if (role == null) {
                return null;   // refresh token 을 Authorization 헤더로 보낸 경우
            }
            AuthPrincipal principal =
                    new AuthPrincipal(Long.valueOf(claims.getSubject()), UserRole.valueOf(role));
            Date issuedAt = claims.getIssuedAt();
            return new AccessToken(principal, issuedAt == null ? null : issuedAt.toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
