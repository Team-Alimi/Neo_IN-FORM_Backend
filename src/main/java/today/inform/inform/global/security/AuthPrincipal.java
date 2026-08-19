package today.inform.inform.global.security;

import today.inform.inform.user.entity.UserRole;

/**
 * 인증된 요청의 principal. JWT 필터가 SecurityContext 에 넣습니다.
 *
 * <p>DB 조회 없이 토큰만으로 구성합니다. 요청마다 사용자를 다시 읽으면
 * 모든 API 에 SELECT 가 한 번씩 더 붙습니다.
 * 권한이 방금 바뀐 사용자는 access token 만료(1시간) 후 반영됩니다.
 */
public record AuthPrincipal(Long userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
