package today.inform.inform.global.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SecurityContext 에서 사용자 id 를 꺼냅니다.
 * 감사 로그 행위자({@code app.changed_by_user_id}) 주입에 쓰입니다.
 *
 * <p>비어 있으면 감사 로그의 {@code changed_by} 가 NULL 로 남고,
 * 이는 스키마상 "크롤러/스케줄러가 한 변경"을 뜻합니다.
 * 관리자 요청에서 비어 있으면 안 됩니다.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return (authentication.getPrincipal() instanceof AuthPrincipal principal)
                ? Optional.of(principal.userId())
                : Optional.empty();
    }
}
