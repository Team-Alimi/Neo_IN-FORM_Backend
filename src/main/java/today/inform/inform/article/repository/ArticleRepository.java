package today.inform.inform.article.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;

/**
 * 공지 쓰기 저장소.
 *
 * <p><b>목록·검색 조회는 여기 두지 않습니다.</b> 피드 응답에는 북마크 여부·제공처 이름처럼
 * 다른 테이블 값이 섞이고, 엔티티로 받으면 목록 한 페이지에 N+1 이 납니다.
 * 그쪽은 DTO projection 을 쓰는 별도 조회 저장소가 담당합니다.
 */
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /** 서비스 노출용 단건 조회. 관리자 경로는 {@code findById} 를 씁니다. */
    Optional<Article> findByIdAndStatus(Long id, ArticleStatus status);

    /**
     * AI 요약 저장. native UPDATE 로 metadata 를 유지하고 시작 snapshot 과 빈 캐시를 검사합니다.
     *
     * <p><b>왜 native 인가</b> — 엔티티로 저장하면 두 가지가 따라옵니다.
     * <ul>
     *   <li>{@code version} 증가 — 요약이 생성될 때마다 관리자의 수정이 낙관적 잠금에 걸립니다</li>
     *   <li>{@code updated_at} 갱신 — 아무도 고치지 않았는데 "수정됨" 으로 보입니다</li>
     * </ul>
     * SET 목록에 summary 만 있으면 화이트리스트 트리거도 발동하지 않습니다.
     *
     * <p><b>왜 {@code updated_at} 으로 가드하는가</b> — 요약 생성은 오래 걸리는 비동기 작업입니다.
     * 그 사이 크롤러가 본문을 갱신하면 트리거가 요약을 NULL 로 지우는데,
     * 뒤늦게 도착한 이 UPDATE 가 가드 없이 실행되면 <b>옛 본문의 요약이 새 본문에 영구히 붙습니다.</b>
     * 트리거가 지운 것을 앱이 되살리는 셈이라 이후 어떤 무효화도 이 값을 건드리지 않습니다.
     *
     * <p>v11 의 version 및 summary IS NULL 조건과 기존 updated_at 보호를 함께 유지합니다.
     * V7 이후 크롤러의 실제 원문 변경은 DB 가 version 을 올립니다.
     * updated_at 은 원문 외에 상태·배포 시각·작성자 변경에도 갱신되는 보수적 보호입니다.
     *
     * @param sourceUpdatedAt 요약을 만들 때 읽은 본문의 {@code updated_at}
     * @param sourceVersion 같은 snapshot 에서 읽은 {@code version}
     * @return 1 이면 반영. 0 이면 snapshot 변경·삭제·캐시 선점으로 폐기합니다.
     *         새 원문의 생성은 이후 상세 요청이 유발하며 자동 재계산 루프는 두지 않습니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE articles SET summary = :summary "
            + "WHERE id = :id AND version = :sourceVersion "
            + "AND updated_at = :sourceUpdatedAt AND summary IS NULL", nativeQuery = true)
    int updateSummary(@Param("id") Long id,
                      @Param("summary") String summary,
                      @Param("sourceUpdatedAt") OffsetDateTime sourceUpdatedAt,
                      @Param("sourceVersion") Long sourceVersion);

    /**
     * 조회수 반영. Redis 에 모아 둔 delta 를 배치가 합산해 넣습니다.
     *
     * <p>{@code view_count = view_count + :delta} 로 <b>DB 에서 더합니다.</b>
     * 읽어서 더한 뒤 쓰면 그 사이의 다른 배치 결과를 덮어씁니다.
     * 요약과 달리 가드가 없어도 되는 이유는 <b>절대값이 아니라 증분</b>이기 때문입니다.
     * 본문이 바뀌어도 그동안 쌓인 조회수는 여전히 유효합니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE articles SET view_count = view_count + :delta WHERE id = :id",
            nativeQuery = true)
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);
}
