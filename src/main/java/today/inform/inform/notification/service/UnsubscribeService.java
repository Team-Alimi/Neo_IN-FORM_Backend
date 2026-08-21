package today.inform.inform.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.repository.UserRepository;

/**
 * USER-04 메일 수신거부.
 *
 * <p><b>실패해도 이유를 나눠 알려 주지 않습니다.</b> 서명이 유효한지, 그 번호의 사용자가 있는지를
 * 응답으로 구분해 주면 <b>링크 하나로 가입 여부를 확인하는 수단</b>이 됩니다.
 * 서명 검증에서 걸리는 것은 {@code UnsubscribeTokenProvider} 가 400 하나로 처리하고,
 * 그 뒤로는 여기서 조용히 성공 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnsubscribeService {

    private final UnsubscribeTokenProvider tokenProvider;
    private final UserRepository userRepository;

    /**
     * 서명이 유효하면 메일 수신을 끕니다.
     *
     * <p><b>멱등입니다.</b> 이미 꺼져 있으면 아무것도 하지 않고 성공으로 끝납니다 —
     * 메일함에서 링크를 두 번 누르는 일은 흔하고, 두 번째에 오류가 뜨면
     * 사용자는 수신거부가 안 된 줄 알고 계속 시도합니다.
     *
     * <p>탈퇴한 계정도 막지 않습니다. 어차피 발송 대상에서 빠지지만
     * ({@code status = 'ACTIVE'} 조건), 여기서 404 를 주면 위 문단의 노출 문제가 생깁니다.
     */
    @Transactional
    public void unsubscribe(String token) {
        Long userId = tokenProvider.parse(token);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // 서명은 우리가 만든 것이므로 여기 오는 것은 그 뒤 계정이 사라진 경우뿐입니다.
            // 사용자에게는 성공으로 보이고, 이상 징후는 로그로만 남깁니다.
            log.warn("수신거부 대상 사용자를 찾지 못했습니다. userId={}", userId);
            return;
        }
        user.changeEmailNotification(false);
    }
}
