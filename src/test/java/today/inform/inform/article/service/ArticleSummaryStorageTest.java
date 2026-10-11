package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import today.inform.inform.article.repository.ArticleRepository;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Flyway 를 실행하지 않고 격리 PostgreSQL 의 작은 fixture 에서 실제 repository SQL 을 검증합니다. */
class ArticleSummaryStorageTest {
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("summary_test").withUsername("summary_test").withPassword("local-test-only");
    private static JdbcTemplate jdbc;
    private static NamedParameterJdbcTemplate named;
    private static String update;
    private Long id;
    private OffsetDateTime snapshot;
    private long version;

    @BeforeAll
    static void fixture() throws Exception {
        DB.start();
        DriverManagerDataSource data = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(data);
        named = new NamedParameterJdbcTemplate(data);
        jdbc.execute("""
                CREATE TABLE articles (
                    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    title text, content text, starts_on date, ends_on date, summary text,
                    version bigint DEFAULT 0, updated_at timestamptz DEFAULT now(),
                    status text DEFAULT 'PUBLISHED', published_at timestamptz, created_by bigint,
                    source_type text DEFAULT 'SCHOOL', similarity_score numeric, similar_article_id bigint)
                """);
        jdbc.execute("CREATE TABLE article_vendors(article_id bigint, source_url text)");
        String v3 = Files.readString(Path.of("src/main/resources/db/migration/V3__triggers.sql"));
        String v7 = Files.readString(Path.of("src/main/resources/db/migration/V7__crawler_bumps_version.sql"));
        installFunction(v3, "inform_articles_summary_invalidate");
        installFunction(v3, "inform_articles_updated_at");
        installFunction(v7, "inform_articles_crawler_policy");
        jdbc.execute("CREATE TRIGGER summary_invalidate BEFORE UPDATE ON articles "
                + "FOR EACH ROW EXECUTE FUNCTION inform_articles_summary_invalidate()");
        jdbc.execute("CREATE TRIGGER updated_at BEFORE UPDATE ON articles "
                + "FOR EACH ROW EXECUTE FUNCTION inform_articles_updated_at()");
        jdbc.execute("CREATE TRIGGER crawler_policy BEFORE UPDATE ON articles "
                + "FOR EACH ROW EXECUTE FUNCTION inform_articles_crawler_policy()");
        jdbc.execute("CREATE ROLE inform_crawler LOGIN PASSWORD 'local-crawler-test-only'");
        jdbc.execute("GRANT SELECT ON articles TO inform_crawler");
        jdbc.execute("GRANT UPDATE(title,content,starts_on,ends_on,version,similarity_score,similar_article_id) "
                + "ON articles TO inform_crawler");
        Method method = Arrays.stream(ArticleRepository.class.getMethods())
                .filter(m -> m.getName().equals("updateSummary")).findFirst().orElseThrow();
        update = method.getAnnotation(Query.class).value();
    }

    private static void installFunction(String migration, String name) {
        int start = migration.indexOf("CREATE OR REPLACE FUNCTION " + name);
        int end = migration.indexOf("END $$;", start) + "END $$;".length();
        jdbc.execute(migration.substring(start, end));
    }

    @AfterAll
    static void stop() { DB.stop(); }

    @BeforeEach
    void article() {
        jdbc.execute("TRUNCATE articles, article_vendors");
        id = jdbc.queryForObject("""
                INSERT INTO articles(title,content,starts_on,ends_on,updated_at)
                VALUES ('제목','본문','2026-10-11','2026-10-15','2000-01-01') RETURNING id
                """, Long.class);
        snapshot = jdbc.queryForObject("SELECT updated_at FROM articles WHERE id=?", OffsetDateTime.class, id);
        version = jdbc.queryForObject("SELECT version FROM articles WHERE id=?", Long.class, id);
    }

    private int save(String summary) {
        return named.update(update, Map.of("id", id, "summary", summary,
                "sourceUpdatedAt", snapshot, "sourceVersion", version));
    }

    @Test
    @DisplayName("요약 저장은 version 과 updated_at 을 변경하지 않고 기존 요약을 덮어쓰지 않는다")
    void cachesWithoutChangingMetadataOrOverwriting() {
        assertThat(save("세 줄 요약")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM articles WHERE id=?", Long.class, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM articles WHERE id=?", OffsetDateTime.class, id))
                .isEqualTo(snapshot);
        assertThat(save("뒤늦게 도착한 요약")).isZero();
        assertThat(jdbc.queryForObject("SELECT summary FROM articles WHERE id=?", String.class, id))
                .isEqualTo("세 줄 요약");
    }

    @Test
    @DisplayName("version 만 바뀌거나 updated_at 만 바뀌어도 이전 snapshot 은 저장되지 않는다")
    void guardsBothVersionAndTimestamp() {
        jdbc.update("UPDATE articles SET version=version+1 WHERE id=?", id);
        assertThat(save("오래된 요약")).isZero();
        jdbc.update("UPDATE articles SET version=?, updated_at=now() WHERE id=?", version, id);
        assertThat(save("오래된 요약")).isZero();
    }

    @Test
    @DisplayName("각 원문 필드의 실제 변경은 무효화하며 동일 값·유사도·출처 URL 변경은 유지한다")
    void invalidationUsesExistingTriggerOnly() {
        for (String assignment : new String[] {"title='새 제목'", "content='새 본문'",
                "starts_on='2026-10-12'", "ends_on='2026-10-16'"}) {
            article();
            assertThat(save("기존 요약")).isEqualTo(1);
            jdbc.update("UPDATE articles SET " + assignment + " WHERE id=?", id);
            assertThat(jdbc.queryForObject("SELECT summary FROM articles WHERE id=?", String.class, id)).isNull();
            assertThat(save("오래된 요약")).isZero();
        }
        article();
        save("유지할 요약");
        jdbc.update("UPDATE articles SET title=title, content=content, starts_on=starts_on, ends_on=ends_on,"
                + "similarity_score=80 WHERE id=?", id);
        jdbc.update("INSERT INTO article_vendors VALUES (?, 'https://example.invalid/original')", id);
        jdbc.update("UPDATE article_vendors SET source_url='https://example.invalid/changed' WHERE article_id=?", id);
        assertThat(jdbc.queryForObject("SELECT summary FROM articles WHERE id=?", String.class, id)).isEqualTo("유지할 요약");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM articles WHERE id=?", OffsetDateTime.class, id))
                .isEqualTo(snapshot);
    }

    @Test
    @DisplayName("실제 crawler 롤에서 no-op 은 요약을 유지하고 원문 변경은 무효화와 version 증가를 수행한다")
    void crawlerCannotWriteSummaryAndBumpsVersionOnRealChanges() throws Exception {
        save("기존 요약");
        try (Connection crawler = DriverManager.getConnection(DB.getJdbcUrl(), "inform_crawler",
                "local-crawler-test-only")) {
            try (var statement = crawler.prepareStatement("UPDATE articles SET content=content WHERE id=?")) {
                statement.setLong(1, id); statement.executeUpdate();
            }
            assertThat(jdbc.queryForObject("SELECT summary FROM articles WHERE id=?", String.class, id))
                    .isEqualTo("기존 요약");
            assertThatThrownBy(() -> crawler.createStatement().executeUpdate(
                    "UPDATE articles SET summary='금지' WHERE id=" + id)).isInstanceOf(java.sql.SQLException.class);
            try (var statement = crawler.prepareStatement("UPDATE articles SET content='새 원문' WHERE id=?")) {
                statement.setLong(1, id); statement.executeUpdate();
            }
        }
        assertThat(jdbc.queryForObject("SELECT version FROM articles WHERE id=?", Long.class, id)).isEqualTo(version + 1);
        assertThat(jdbc.queryForObject("SELECT summary FROM articles WHERE id=?", String.class, id)).isNull();
        assertThat(save("오래된 요약")).isZero();
    }
}
