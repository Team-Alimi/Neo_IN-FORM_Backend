package today.inform.inform_backend.repository;

import org.springframework.data.repository.CrudRepository;
import today.inform.inform_backend.entity.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
}
