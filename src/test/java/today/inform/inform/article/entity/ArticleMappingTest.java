package today.inform.inform.article.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.support.IntegrationTest;

/**
 * Article 매핑이 "DB 가 소유한 컬럼을 앱이 덮어쓰지 않는다" 는 약속을 지키는지 확인합니다.
 *
 * <p>단위 테스트로는 확인할 수 없는 것들입니다. 트리거·생성 컬럼·DEFAULT 가
 * 실제로 도는 DB 위에서만 드러나는 문제라서 통합 테스트로 씁니다.
 */
@Transactional
class ArticleMappingTest extends IntegrationTest {

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // 생성 컬럼
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("search_text 와 period 는 매핑하지 않아도 DB 가 채운다")
    void generatedColumnsAreFilledByDatabase() {
        Article article = save(Article.createSchoolArticle(
                "2026 국가장학금 <b>신청</b> 안내", "<p>신청 기간 안내</p>",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null));

        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT search_text, period IS NOT NULL FROM articles WHERE id = :id")
                .setParameter("id", article.getId())
                .getSingleResult();

        // 태그가 제거되고 소문자화됩니다 — inform_normalize_search
        assertThat((String) row[0]).doesNotContain("<b>").contains("국가장학금");
        assertThat((Boolean) row[1]).isTrue();
    }

    @Test
    @DisplayName("마감일만 있으면 period 는 NULL 이다 — CASE 가드가 없으면 하한 무한대가 된다")
    void periodIsNullWhenOnlyEndDateGiven() {
        Article article = save(Article.createSchoolArticle(
                "마감만 있는 공지", "내용", null, LocalDate.of(2026, 3, 31), null));

        Boolean hasPeriod = (Boolean) em.createNativeQuery(
                        "SELECT period IS NOT NULL FROM articles WHERE id = :id")
                .setParameter("id", article.getId())
                .getSingleResult();

        assertThat(hasPeriod).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 카운터 — 이 매핑의 존재 이유
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 다른 트랜잭션이 올린 카운터를 앱의 UPDATE 가 덮어쓰지 않는다")
    void counterIsNotOverwrittenByStaleEntity() {
        Article article = save(Article.createSchoolArticle("제목", "내용", null, null, null));
        Long id = article.getId();
        assertThat(article.getBookmarkCount()).isZero();

        // 다른 사용자가 북마크를 누른 상황. 트리거가 bookmark_count 를 올립니다.
        Long userId = createUser("counter@inha.ac.kr");
        em.createNativeQuery("INSERT INTO bookmarks (user_id, article_id) VALUES (:u, :a)")
                .setParameter("u", userId).setParameter("a", id)
                .executeUpdate();

        // 엔티티는 아직 bookmarkCount=0 을 들고 있습니다. 이 상태로 제목만 고칩니다.
        article.edit("고친 제목", "내용", null, null);
        em.flush();

        Integer stored = (Integer) em.createNativeQuery(
                        "SELECT bookmark_count FROM articles WHERE id = :id")
                .setParameter("id", id).getSingleResult();

        // updatable=false 가 없으면 여기서 0 으로 되돌아갑니다.
        assertThat(stored).isEqualTo(1);

        // 메모리 값은 아직 0 입니다. UPDATE 시점 @Generated 를 붙이면 자동으로 맞출 수 있지만
        // 그 대가로 낙관적 잠금이 죽습니다(ArticleOptimisticLockTest 참조). 명시적으로 읽습니다.
        assertThat(article.getBookmarkCount()).isZero();
        em.refresh(article);
        assertThat(article.getBookmarkCount()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // summary
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("제목을 고치면 트리거가 summary 를 지운다 (refresh 후 확인)")
    void summaryIsInvalidatedByTrigger() {
        Article article = save(Article.createSchoolArticle("원래 제목", "내용", null, null, null));

        int applied = articleRepository.updateSummary(
                article.getId(), "AI 가 만든 요약", article.getUpdatedAt());
        assertThat(applied).isEqualTo(1);

        Article reloaded = articleRepository.findById(article.getId()).orElseThrow();
        assertThat(reloaded.getSummary()).isEqualTo("AI 가 만든 요약");

        reloaded.edit("고친 제목", "내용", null, null);
        em.flush();
        em.refresh(reloaded);

        assertThat(reloaded.getSummary()).isNull();
    }

    @Test
    @DisplayName("★ 요약을 만드는 사이 본문이 바뀌면 그 요약은 반영되지 않는다")
    void staleSummaryIsRejected() {
        Article article = save(Article.createSchoolArticle("원래 제목", "원래 내용", null, null, null));
        Long id = article.getId();

        // now() 는 트랜잭션 시작 시각으로 고정이라(updatedAtFollowsWhitelist 참조)
        // 같은 트랜잭션 안에서는 트리거가 updated_at 을 움직일 수 없습니다.
        // 요약 작업이 읽은 시점을 과거로 밀어 두어야 "그 사이 본문이 바뀐" 상황이 만들어집니다.
        em.createNativeQuery("UPDATE articles SET updated_at = TIMESTAMPTZ '2000-01-01' WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        em.clear();
        OffsetDateTime snapshot = articleRepository.findById(id).orElseThrow().getUpdatedAt();

        // 요약이 만들어지는 동안 크롤러가 본문을 갱신했습니다. 트리거가 updated_at 을 현재로 올립니다.
        em.createNativeQuery("UPDATE articles SET content = '새 내용' WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        em.clear();

        int applied = articleRepository.updateSummary(id, "옛 내용의 요약", snapshot);

        assertThat(applied)
                .as("가드가 없으면 옛 본문의 요약이 새 본문에 영구히 붙습니다")
                .isZero();
        assertThat(articleRepository.findById(id).orElseThrow().getSummary()).isNull();
    }

    @Test
    @DisplayName("요약 저장과 조회수 반영은 version 도 updated_at 도 건드리지 않는다")
    void summaryAndViewCountDoNotTouchVersionOrUpdatedAt() {
        Article article = save(Article.createSchoolArticle("제목", "내용", null, null, null));
        Long id = article.getId();
        Long versionBefore = article.getVersion();
        OffsetDateTime updatedBefore = article.getUpdatedAt();

        articleRepository.updateSummary(id, "요약", updatedBefore);
        articleRepository.addViewCount(id, 42);

        Article reloaded = articleRepository.findById(id).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedBefore);
        assertThat(reloaded.getViewCount()).isEqualTo(42);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updated_at 화이트리스트
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * <b>이 테스트가 왜 updated_at 을 과거로 밀어 두고 시작하는가</b>
     *
     * <p>PostgreSQL 의 {@code now()} 는 <b>트랜잭션 시작 시각</b>입니다. 문장이 실행된 시각이 아닙니다.
     * 테스트 전체가 한 트랜잭션 안에서 도니까 트리거가 {@code updated_at := now()} 를
     * 아무리 여러 번 실행해도 값이 INSERT 때와 똑같습니다.
     * "갱신됐는지" 를 시각 비교로 확인할 방법이 없습니다.
     *
     * <p>그래서 반대로 봅니다. {@code updated_at} 을 2000년으로 밀어 두면
     * 트리거가 발동했을 때만 현재 시각으로 되돌아옵니다. 발동 여부가 그대로 드러납니다.
     */
    @Test
    @DisplayName("업무 컬럼이 바뀔 때만 updated_at 이 갱신된다")
    void updatedAtFollowsWhitelist() {
        Article article = save(Article.createSchoolArticle("제목", "내용", null, null, null));
        Long id = article.getId();

        // 화이트리스트 밖 컬럼만 건드리는 UPDATE 라 트리거가 발동하지 않고, 준 값이 그대로 저장됩니다.
        // ★ 연도로 단정하지 않습니다. pgjdbc 가 세션 타임존을 JVM 기본값으로 맞추기 때문에
        //   '2000-01-01' 이 KST 로 해석되어 UTC 로는 1999년이 됩니다. 읽어 온 값 자체를 기준으로 씁니다.
        em.createNativeQuery("UPDATE articles SET updated_at = TIMESTAMPTZ '2000-01-01' WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
        em.clear();

        Article loaded = articleRepository.findById(id).orElseThrow();
        OffsetDateTime backdated = loaded.getUpdatedAt();
        assertThat(backdated).isBefore(OffsetDateTime.now().minusYears(1));

        loaded.markSimilarTo(new java.math.BigDecimal("85.00"), null);   // 화이트리스트 밖
        em.flush();
        em.refresh(loaded);
        assertThat(loaded.getUpdatedAt())
                .as("유사도 판정만으로 수정 시각이 바뀌면 안 됩니다")
                .isEqualTo(backdated);

        loaded.edit("고친 제목", "내용", null, null);                      // 화이트리스트 안
        em.flush();
        em.refresh(loaded);
        assertThat(loaded.getUpdatedAt())
                .as("제목이 바뀌면 수정 시각이 갱신되어야 합니다")
                .isAfter(backdated);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 상태 전이
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("검수를 건너뛴 발행은 막고, 정상 경로는 published_at 을 찍는다")
    void statusTransitionRules() {
        Article article = save(Article.createSchoolArticle("제목", "내용", null, null, null));
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> article.changeStatus(ArticleStatus.PUBLISHED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> article.changeStatus(ArticleStatus.DRAFT))
                .isInstanceOf(BusinessException.class);   // SCHOOL 에 없는 상태

        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();

        // 앱이 아니라 트리거가 찍으므로 메모리 값은 아직 비어 있습니다.
        assertThat(article.getPublishedAt()).isNull();
        em.refresh(article);
        OffsetDateTime firstPublish = article.getPublishedAt();
        assertThat(firstPublish).isNotNull();

        // now() 가 트랜잭션 고정이라 재발행 시각과 최초 발행 시각이 우연히 같아질 수 있습니다.
        // 최초 값을 과거로 밀어 두면 트리거가 덮어썼는지 아닌지가 분명해집니다.
        em.createNativeQuery("UPDATE articles SET published_at = TIMESTAMPTZ '2000-01-01' WHERE id = :id")
                .setParameter("id", article.getId()).executeUpdate();
        em.refresh(article);
        OffsetDateTime backdated = article.getPublishedAt();

        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);   // 배포 취소
        article.changeStatus(ArticleStatus.PUBLISHED);          // 재발행
        em.flush();
        em.refresh(article);

        // 재발행이 발행 시각을 바꾸면 피드 정렬이 흔들립니다.
        assertThat(article.getPublishedAt())
                .as("트리거는 비어 있을 때만 채워야 합니다")
                .isEqualTo(backdated);
    }

    @Test
    @DisplayName("★ 크롤러가 status=PUBLISHED 로 직접 INSERT 해도 발행 시각이 채워진다")
    void publishedAtIsFilledOnDirectInsert() {
        // 크롤러는 앱을 거치지 않고 정상 공지를 곧바로 노출시킵니다.
        // published_at 을 주지 않아도 ck_articles_published 를 통과해야 합니다.
        em.createNativeQuery(
                        "INSERT INTO articles (source_type, title, content, status) "
                                + "VALUES ('SCHOOL', '크롤러가 넣은 공지', '내용', 'PUBLISHED')")
                .executeUpdate();

        Object result = em.createNativeQuery(
                        "SELECT published_at FROM articles WHERE title = '크롤러가 넣은 공지'")
                .getSingleResult();

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("발행된 동아리 공지는 휴지통으로만 내릴 수 있다")
    void publishedClubArticleCanOnlyGoToTrash() {
        Article article = save(Article.createClubArticle("동아리 모집", "내용", null, null, null));
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);

        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();

        // 학교 공지의 "배포 취소" 에 해당하는 경로가 동아리에는 없습니다(POLICY 전이 표).
        assertThatThrownBy(() -> article.changeStatus(ArticleStatus.DRAFT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> article.changeStatus(ArticleStatus.READY_TO_PUBLISH))
                .isInstanceOf(BusinessException.class);   // CLUB 에 없는 상태

        article.changeStatus(ArticleStatus.TRASHED);
        em.flush();
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.TRASHED);

        // 되살리려면 휴지통 복구를 씁니다.
        article.restoreTo(ArticleStatus.DRAFT);
        em.flush();
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    @DisplayName("휴지통 복구는 직전 상태로만 간다")
    void restoreOnlyToPreviousStatus() {
        Article article = save(Article.createSchoolArticle("제목", "내용", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.TRASHED);
        em.flush();

        assertThatThrownBy(() -> article.restoreTo(ArticleStatus.DRAFT))
                .isInstanceOf(BusinessException.class);   // SCHOOL 에 없는 상태

        article.restoreTo(ArticleStatus.READY_TO_PUBLISH);
        em.flush();
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.READY_TO_PUBLISH);
    }

    @Test
    @DisplayName("상태가 바뀔 때마다 감사 로그가 남는다 — INSERT 포함")
    void statusChangesAreAudited() {
        Article article = save(Article.createSchoolArticle("제목", "내용", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        em.flush();

        Long logCount = ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM article_status_logs WHERE article_id = :id")
                .setParameter("id", article.getId()).getSingleResult()).longValue();

        // 1) NULL -> PENDING_REVIEW (INSERT)  2) PENDING_REVIEW -> READY_TO_PUBLISH
        assertThat(logCount).isEqualTo(2);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 DB 까지 가기 전에 막는다")
    void periodOrderIsValidatedInApplication() {
        assertThatThrownBy(() -> Article.createSchoolArticle(
                "제목", "내용", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Article save(Article article) {
        Article saved = articleRepository.save(article);
        em.flush();
        return saved;
    }

    private Long createUser(String email) {
        em.createNativeQuery(
                        "INSERT INTO users (email, name, role, status) "
                                + "VALUES (:email, '테스트', 'USER', 'ACTIVE')")
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }
}
