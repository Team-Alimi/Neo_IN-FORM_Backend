package today.inform.inform.global.security;

import java.time.Instant;

/**
 * 해석된 Access Token.
 *
 * <p>{@code issuedAt} 을 {@link AuthPrincipal} 에 넣지 않고 여기서 감쌉니다.
 * 발급 시각은 무효화 판정에만 쓰는 토큰 메타데이터고,
 * 컨트롤러·서비스가 다루는 "누가 요청했는가" 와는 다른 관심사입니다.
 */
public record AccessToken(AuthPrincipal principal, Instant issuedAt) {
}
