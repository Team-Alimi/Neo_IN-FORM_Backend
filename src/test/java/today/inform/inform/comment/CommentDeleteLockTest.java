package today.inform.inform.comment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.support.IntegrationTest;

/**
 * 댓글 삭제가 잡는 잠금이 <b>답글 INSERT 를 실제로 막는지</b> 확인합니다.
 *
 * <p><b>왜 이걸 따로 확인해야 하는가</b>
 * PostgreSQL 의 행 잠금은 종류마다 충돌 상대가 다릅니다.
 * 답글을 넣으면 외래 키 때문에 부모 행에 {@code FOR KEY SHARE} 가 걸리는데,
 * 이건 {@code FOR NO KEY UPDATE} 와는 <b>충돌하지 않습니다.</b>
 * 즉 잠금을 걸어 두고도 답글이 끼어들 수 있습니다.
 *
 * <p>Hibernate 6+ 는 {@code LockModeType.PESSIMISTIC_WRITE} 를
 * {@code FOR NO KEY UPDATE} 로 내보냅니다. JPA 표기만 보고는 알 수 없어서
 * 실제 DB 동작으로 확인해 둡니다.
 *
 * <p>{@code @Transactional} 이 없습니다 — 잠금 충돌을 보려면 커밋된 데이터와
 * 별도 커넥션 두 개가 필요합니다.
 */
class CommentDeleteLockTest extends IntegrationTest {

    private static final String LOCK_SQL = "SELECT id FROM comments WHERE id = %d FOR UPDATE";
    private static final String WEAK_LOCK_SQL = "SELECT id FROM comments WHERE id = %d FOR NO KEY UPDATE";

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("★ FOR UPDATE 는 답글 INSERT 를 막는다 — 삭제 판정 중에 답글이 끼어들 수 없다")
    void forUpdateBlocksReplyInsert() throws SQLException {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> attemptReplyWhileLocked(fixture, LOCK_SQL))
                .as("막히지 않으면 '답글 없음' 으로 판정한 직후 달린 답글이 CASCADE 로 함께 지워집니다")
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("lock timeout");
    }

    @Test
    @DisplayName("FOR NO KEY UPDATE 였다면 답글이 그대로 끼어든다 — 왜 약한 잠금을 쓰면 안 되는지")
    void weakLockDoesNotBlockReplyInsert() throws SQLException {
        Fixture fixture = createFixture();

        assertThatCode(() -> attemptReplyWhileLocked(fixture, WEAK_LOCK_SQL))
                .as("Hibernate 의 PESSIMISTIC_WRITE 가 내보내는 것이 바로 이 잠금입니다")
                .doesNotThrowAnyException();
    }

    /**
     * 커넥션 A 가 원댓글을 잠근 상태에서 커넥션 B 가 답글을 넣어 봅니다.
     *
     * <p>B 에 짧은 {@code lock_timeout} 을 걸어 두어, 막히면 기다리지 않고 바로 오류가 납니다.
     * 안 막히면 INSERT 가 그대로 성공합니다. 두 결과가 명확히 갈립니다.
     */
    private void attemptReplyWhileLocked(Fixture fixture, String lockSql) throws SQLException {
        try (Connection locker = appConnection();
             Connection inserter = appConnection()) {

            locker.setAutoCommit(false);
            try (Statement statement = locker.createStatement()) {
                statement.execute(String.format(lockSql, fixture.rootCommentId()));
            }

            inserter.setAutoCommit(false);
            try (Statement statement = inserter.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '500ms'");
                statement.execute(String.format(
                        "INSERT INTO comments (article_id, user_id, parent_id, content) "
                                + "VALUES (%d, %d, %d, '끼어든 답글')",
                        fixture.articleId(), fixture.userId(), fixture.rootCommentId()));
            } finally {
                inserter.rollback();
            }

            locker.rollback();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private record Fixture(Long articleId, Long userId, Long rootCommentId) {
    }

    /** 별도 커넥션에서 보여야 하므로 커밋합니다. 테스트가 끝나면 지웁니다. */
    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            Long articleId = scalar("""
                    INSERT INTO articles (source_type, title, content, status)
                    VALUES ('SCHOOL', '잠금 테스트 공지', '내용', 'PUBLISHED') RETURNING id
                    """);
            Long userId = scalar("""
                    INSERT INTO users (email, name, role, status)
                    VALUES ('lock-test@inha.ac.kr', '잠금테스터', 'USER', 'ACTIVE') RETURNING id
                    """);
            Long commentId = scalar(String.format("""
                    INSERT INTO comments (article_id, user_id, content)
                    VALUES (%d, %d, '지울 원댓글') RETURNING id
                    """, articleId, userId));
            return new Fixture(articleId, userId, commentId);
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery(
                                "DELETE FROM articles WHERE title = '잠금 테스트 공지'")
                        .executeUpdate());
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery(
                                "DELETE FROM users WHERE email = 'lock-test@inha.ac.kr'")
                        .executeUpdate());
    }

    private Long scalar(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
