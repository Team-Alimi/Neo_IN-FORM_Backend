package today.inform.inform.comment.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform.comment.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 삭제 판정 전에 대상 댓글 행을 잠급니다.
     *
     * <p>잠그지 않으면 "답글이 있나?" 를 확인한 직후, 실제로 지우기 전에 답글이 달릴 수 있습니다.
     * {@code parent_id} 가 {@code ON DELETE CASCADE} 라 그 답글까지 <b>조용히 함께 지워집니다.</b>
     * 사용자는 자기 댓글 하나를 지웠는데 남의 답글이 사라진 것도 모릅니다.
     *
     * <p><b>★ {@code @Lock(PESSIMISTIC_WRITE)} 을 쓰면 안 됩니다.</b>
     * Hibernate 6+ 는 그걸 PostgreSQL 의 {@code FOR NO KEY UPDATE} 로 내보내는데,
     * 이 잠금은 자식 INSERT 가 부모 행에 잡는 {@code FOR KEY SHARE} 와
     * <b>충돌하지 않습니다.</b> 잠금을 걸어 둔 채로 답글이 그대로 들어옵니다.
     * ({@code CommentDeleteLockTest} 가 두 잠금의 차이를 실제 DB 로 보여 줍니다)
     *
     * <p>그래서 native 로 {@code FOR UPDATE} 를 직접 씁니다.
     * 행 전체가 아니라 id 만 읽는 이유는 잠그는 것이 목적이기 때문입니다 —
     * 엔티티는 이어서 {@code findById} 로 영속성 컨텍스트에 올립니다.
     *
     * @return 잠근 댓글의 id. 비어 있으면 그 댓글이 없는 것입니다.
     */
    @Query(value = "SELECT id FROM comments WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("id") Long id);

    boolean existsByParentId(Long parentId);

    /** 알림 본문에 넣을 공지 제목. 알림 한 건 때문에 공지 엔티티를 통째로 읽지 않습니다. */
    @Query("SELECT a.title FROM today.inform.inform.article.entity.Article a WHERE a.id = :articleId")
    Optional<String> findArticleTitle(@Param("articleId") Long articleId);

    /**
     * CMT-02 원댓글 목록. 답글은 {@link #findReplies} 가 따로 가져옵니다.
     *
     * <p>삭제된 원댓글도 포함합니다 — 답글이 달려 있어 자리를 남긴 경우라
     * 빼면 그 아래 답글들이 갈 곳을 잃습니다.
     */
    @Query("""
            SELECT new today.inform.inform.comment.repository.CommentRow(
                       c.id, c.parentId, c.content, c.deletedAt, c.createdAt, c.updatedAt,
                       u.id, u.name, u.status)
              FROM Comment c
              JOIN today.inform.inform.user.entity.User u ON u.id = c.userId
             WHERE c.articleId = :articleId AND c.parentId IS NULL
             ORDER BY c.createdAt ASC, c.id ASC
            """)
    Page<CommentRow> findRoots(@Param("articleId") Long articleId, Pageable pageable);

    /**
     * 단건 조회. 작성 직후 응답을 만들 때 씁니다.
     *
     * <p>엔티티에서 직접 응답을 만들지 않는 이유는 작성자 이름 때문입니다.
     * 엔티티에는 {@code user_id} 만 있어서 이름이 비게 되고, 그렇다고 응답에서 빼면
     * 방금 쓴 댓글만 작성자 없이 그려집니다. 목록과 같은 projection 을 쓰면
     * 화면이 두 경로에서 같은 모양을 받습니다.
     */
    @Query("""
            SELECT new today.inform.inform.comment.repository.CommentRow(
                       c.id, c.parentId, c.content, c.deletedAt, c.createdAt, c.updatedAt,
                       u.id, u.name, u.status)
              FROM Comment c
              JOIN today.inform.inform.user.entity.User u ON u.id = c.userId
             WHERE c.id = :id
            """)
    Optional<CommentRow> findRowById(@Param("id") Long id);

    /** 원댓글 목록 한 페이지의 답글을 한 번에. 부모마다 부르면 그게 N+1 입니다. */
    @Query("""
            SELECT new today.inform.inform.comment.repository.CommentRow(
                       c.id, c.parentId, c.content, c.deletedAt, c.createdAt, c.updatedAt,
                       u.id, u.name, u.status)
              FROM Comment c
              JOIN today.inform.inform.user.entity.User u ON u.id = c.userId
             WHERE c.parentId IN :parentIds
             ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<CommentRow> findReplies(@Param("parentIds") List<Long> parentIds);
}
