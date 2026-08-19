package today.inform.inform.global.security;

import java.util.Optional;

/**
 * 현재 요청을 수행 중인 사용자 id 를 제공한다.
 *
 * <p>감사 로그의 행위자(`app.changed_by_user_id`) 주입에 쓰인다.
 * 인증 구현(auth 도메인)이 완성되면 SecurityContext 기반 구현으로 교체한다.
 *
 * <p>값이 비어 있으면 감사 로그의 `changed_by` 가 NULL 로 남고, 이는 스키마상
 * "크롤러/스케줄러가 한 변경"을 뜻한다. 관리자 요청에서 비어 있으면 안 된다.
 */
public interface CurrentUserProvider {

    Optional<Long> currentUserId();
}
