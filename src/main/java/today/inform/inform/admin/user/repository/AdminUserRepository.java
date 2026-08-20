package today.inform.inform.admin.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.user.entity.User;
import today.inform.inform.user.entity.UserRole;
import today.inform.inform.user.entity.UserStatus;

/**
 * ADM-16 회원 목록.
 *
 * <p>{@code UserRepository} 와 나눈 이유는 노출 기준이 아니라 <b>쓰임</b>이 다르기 때문입니다.
 * 저쪽은 로그인 경로가 쓰는 단건 조회뿐이고, 여기는 관리 화면 전용 검색입니다.
 */
public interface AdminUserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일·이름 부분 일치 + 권한·상태 필터.
     *
     * <p><b>{@code ESCAPE} 를 함께 적습니다.</b> {@code LikePattern} 이 만든 이스케이프가
     * 그것 없이는 무의미해집니다. 이메일에 밑줄이 흔해서 실제로 문제가 됩니다 —
     * {@code kim_hs} 를 찾으면 {@code kimahs} 같은 것까지 걸립니다.
     *
     * <p><b>정렬을 쿼리에 고정하고 클라이언트 정렬은 서비스가 버립니다.</b>
     * Spring Data 는 {@code Pageable} 의 정렬을 검증 없이 ORDER BY 뒤에 이어 붙여서,
     * {@code ?sort=foo} 하나면 JPQL 파싱에서 터지고 SQLSTATE 가 없어 500 이 나갑니다.
     */
    @Query("""
            SELECT u FROM User u
             WHERE (:role   IS NULL OR u.role   = :role)
               AND (:status IS NULL OR u.status = :status)
               AND (:keyword IS NULL
                    OR lower(u.email) LIKE :keyword ESCAPE '\\'
                    OR lower(u.name)  LIKE :keyword ESCAPE '\\')
             ORDER BY u.createdAt DESC, u.id DESC
            """)
    Page<User> search(@Param("keyword") String keyword,
                      @Param("role") UserRole role,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
