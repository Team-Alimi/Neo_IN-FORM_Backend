package today.inform.inform.article.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.support.IntegrationTest;

/**
 * 크롤러가 앱을 거치지 않고 직접 쓸 때의 규칙을 검증합니다.
 *
 * <p><b>{@code @Transactional} 이 없습니다.</b> 일부러입니다.
 * 크롤러는 별도 프로세스·별도 커넥션이라, 앱이 커밋하지 않은 데이터는 보이지 않습니다.
 * 롤백 방식으로 격리하면 크롤러 쪽에서 아무것도 안 보여 테스트가 성립하지 않습니다.
 * 대신 {@link #cleanUp()} 이 만든 행을 지웁니다.
 */
class CrawlerPolicyTest extends IntegrationTest {

    private static final String TITLE = "크롤러 정책 테스트 공지";

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    /**
     * {@code @Transactional} 을 {@code @AfterEach} 에 붙여도 걸리지 않습니다
     * (테스트 메서드에만 적용됩니다). 트랜잭션을 직접 엽니다.
     */
    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        // 크롤러는 제목을 바꾸지 않으므로 제목만으로 찾을 수 있습니다.
        // article_status_logs 는 ON DELETE CASCADE 라 함께 지워집니다.
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery("DELETE FROM articles WHERE title = :title")
                        .setParameter("title", TITLE)
                        .executeUpdate());
    }

    @Test
    @DisplayName("★ 크롤러가 본문을 바꾸면 재검수 대기로 내려가고 version 이 올라간다")
    void contentChangeDemotesAndBumpsVersion() throws SQLException {
        Article article = publishSchoolArticle();
        Long id = article.getId();
        Long versionBefore = article.getVersion();

        crawlerUpdateContent(id, "크롤러가 바꾼 본문");

        Article after = articleRepository.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ArticleStatus.PENDING_REVIEW);
        assertThat(after.getVersion())
                .as("version 이 그대로면 앱이 강등을 감지하지 못해 검수 없이 발행됩니다")
                .isEqualTo(versionBefore + 1);
        assertThat(after.getPublishedAt())
                .as("published_at 은 유지해야 신규 수집분과 재검수 건이 구분됩니다")
                .isNotNull();
    }

    @Test
    @DisplayName("★ 내용이 그대로면 크롤링 주기마다 UPDATE 가 와도 아무 일이 없다")
    void noOpUpdateChangesNothing() throws SQLException {
        Article article = publishSchoolArticle();
        Long id = article.getId();
        Long versionBefore = article.getVersion();

        // 크롤러는 내용이 같아도 수집 주기마다 UPDATE 를 날립니다.
        crawlerUpdateContent(id, article.getContent());

        Article after = articleRepository.findById(id).orElseThrow();
        assertThat(after.getStatus())
                .as("no-op UPDATE 에 반응하면 주기마다 전체 공지가 검수 큐로 쏟아집니다")
                .isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(after.getVersion())
                .as("version 이 튀면 그 순간 저장하던 관리자가 이유 없이 409 를 받습니다")
                .isEqualTo(versionBefore);
    }

    @Test
    @DisplayName("★ 크롤러가 먼저 바꾸면 관리자의 저장이 거부된다")
    void adminSaveIsRejectedAfterCrawlerChange() throws SQLException {
        Article article = publishSchoolArticle();
        Long id = article.getId();

        // 관리자가 화면을 열어 둔 상태(= 이 시점의 스냅샷)
        Article openedByAdmin = articleRepository.findById(id).orElseThrow();

        crawlerUpdateContent(id, "크롤러가 바꾼 본문");

        openedByAdmin.edit("관리자가 고친 제목", openedByAdmin.getContent(), null, null);

        assertThatThrownBy(() -> articleRepository.saveAndFlush(openedByAdmin))
                .as("이게 통과하면 크롤러의 갱신이 조용히 사라집니다")
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 발행 상태까지 올린 학교 공지. 커밋되므로 크롤러 커넥션에서도 보입니다. */
    private Article publishSchoolArticle() {
        Article article = articleRepository.save(
                Article.createSchoolArticle(TITLE, "원래 본문", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        return articleRepository.saveAndFlush(article);
    }

    /**
     * 크롤러 롤로 본문을 갱신합니다.
     *
     * <p>{@code version} 을 SET 목록에 넣지 않습니다. 크롤러가 규약을 지키지 않는 상황이
     * 바로 이 테스트가 막으려는 것이고, 이제는 DB 가 대신 올려 줍니다.
     */
    private void crawlerUpdateContent(Long id, String content) throws SQLException {
        try (Connection connection = crawlerConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE articles SET content = ? WHERE id = ?")) {
            statement.setString(1, content);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }
}
