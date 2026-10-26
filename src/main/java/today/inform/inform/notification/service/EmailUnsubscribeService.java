package today.inform.inform.notification.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.repository.UserRepository;

/**
 * NTF-05 메일 수신거부.
 *
 * <p>로그인 없이 동작해야 합니다 — 메일을 받은 사람이 그 자리에서 끄지 못하면
 * 수신거부가 아닙니다. 권한은 {@link UnsubscribeTokenSigner} 가 서명한 토큰이 들고 옵니다.
 *
 * <h2>★ 왜 {@code UserService.updateSettings} 를 쓰지 않는가</h2>
 * 그쪽은 {@code findActive} 로 탈퇴자를 걸러 {@code USER_NOT_FOUND} 를 던집니다.
 * 설정 화면에서는 맞는 동작이지만 수신거부 페이지에서는
 * <b>"이 계정은 탈퇴 상태" 라는 사실이 로그인 없이 새어 나갑니다.</b>
 * 토큰만 있으면 계정 상태를 떠볼 수 있게 되는 셈입니다.
 *
 * <h2>★ 성공과 "이미 그런 상태" 를 구분하지 않습니다</h2>
 * 이미 꺼 둔 사용자, 탈퇴한 사용자, 방금 끈 사용자 — 세 경우 모두
 * "메일 알림을 받지 않습니다" 가 사실입니다. 사용자에게 보여 줄 결과가 같고,
 * 구분해서 알려 주면 토큰으로 계정 상태를 확인할 수 있게 됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailUnsubscribeService {

    private final UnsubscribeTokenSigner unsubscribeTokenSigner;
    private final UserRepository userRepository;

    /**
     * 토큰이 유효한지만 봅니다. <b>상태를 바꾸지 않습니다.</b>
     *
     * <p>확인 페이지({@code GET})가 씁니다. 메일 클라이언트와 링크 검사기는
     * 사용자가 누르지 않아도 링크를 미리 받아 봅니다 — {@code GET} 에서 수신거부를
     * 처리하면 <b>메일이 도착한 것만으로 알림이 꺼집니다.</b>
     */
    public boolean isTokenValid(String token) {
        return unsubscribeTokenSigner.verify(token).isPresent();
    }

    /**
     * 실제 수신거부. {@code POST} 에서만 불립니다.
     *
     * @return 토큰이 유효해 처리된 경우 {@code true}.
     *         만료·위조·형식 오류는 {@code false}
     */
    @Transactional
    public boolean unsubscribe(String token) {
        Optional<Long> userId = unsubscribeTokenSigner.verify(token);
        if (userId.isEmpty()) {
            // 어떤 토큰이었는지 로그에 남기지 않습니다. 유효한 토큰이 섞여 들어오면
            // 그 로그 한 줄이 그대로 "이 사람 메일 끄기" 버튼이 됩니다.
            log.info("수신거부 토큰이 유효하지 않습니다");
            return false;
        }

        userRepository.findById(userId.get())
                .filter(User::isActive)
                .ifPresent(user -> user.changeEmailNotification(false));
        // 없거나 탈퇴한 계정이어도 true 입니다 — 결과("메일을 받지 않는다")가 같고,
        // 구분해서 응답하면 계정 존재 여부가 드러납니다.
        log.info("메일 수신거부 처리. 사용자 id={}", userId.get());
        return true;
    }
}
