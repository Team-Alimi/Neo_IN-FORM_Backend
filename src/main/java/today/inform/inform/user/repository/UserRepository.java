package today.inform.inform.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 활성 사용자만 찾습니다.
     *
     * <p>탈퇴는 soft delete 라 행이 남아 있고, 이메일 UNIQUE 도
     * {@code WHERE status='ACTIVE'} partial index 입니다.
     * 상태를 빼고 조회하면 탈퇴자 행이 걸려 재가입이 막힙니다.
     */
    Optional<User> findByEmailAndStatus(String email, UserStatus status);
}
