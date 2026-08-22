package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.response.SimilarComparison;
import today.inform.inform.admin.article.dto.response.StatusLogResponse;
import today.inform.inform.admin.article.service.AdminArticleService;
import today.inform.inform.admin.article.service.ArticleMergeService;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.comment.dto.response.CommentResponse;
import today.inform.inform.comment.service.CommentService;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.support.IntegrationTest;

/**
 * ADM-10 · 12 · 13 — 병합과 영구 삭제.
 *
 * <p><b>여기서 놓치면 데이터가 조용히 사라집니다.</b>
 * {@code articles} 를 참조하는 모든 외래 키가 {@code ON DELETE CASCADE} 라,
 * 옮기지 않은 것은 흡수된 공지를 지우는 순간 오류 없이 없어집니다.
 * 그래서 딸린 것을 <b>종류별로 하나씩</b> 확인합니다.
 */
@Transactional
class ArticleMergeTest extends IntegrationTest {

    @Autowired
    private ArticleMergeService mergeService;

    @Autowired
    private AdminArticleService adminArticleService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;
    private Long userId;
    private Long otherUserId;
    private Long vendorId;
    private Long categoryId;
    private Long secondCategoryId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("merge-admin@inha.ac.kr", "ADMIN");
        userId = insertUser("merge-user@inha.ac.kr", "USER");
        otherUserId = insertUser("merge-other@inha.ac.kr", "USER");
        vendorId = insertVendor("병합테스트학과", "MERGETEST");
        categoryId = firstId("SELECT id FROM categories ORDER BY id");
        secondCategoryId = firstId("SELECT id FROM categories ORDER BY id OFFSET 1");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-13 병합 — 딸린 것이 옮겨지는가
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 출처가 옮겨진다 — 빠뜨리면 다음 수집에서 흡수된 공지가 되살아난다")
    void vendorsAreMoved() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        crawlerVendor(source.getId(), "9999");
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), "중복", adminId);
        em.flush();

        assertThat(externalKeysOf(target.getId()))
                .as("원본 게시판 글 번호가 사라지면 크롤러가 처음 보는 글로 인식합니다")
                .contains("9999");
    }

    @Test
    @DisplayName("★ 북마크·좋아요가 옮겨지고, 둘 다 가진 사용자는 잃는 것이 없다")
    void reactionsAreMoved() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");

        react("bookmarks", userId, source.getId());         // 흡수될 쪽만
        react("bookmarks", otherUserId, source.getId());    // 양쪽 다
        react("bookmarks", otherUserId, target.getId());
        react("article_likes", userId, source.getId());
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), null, adminId);
        em.flush();

        assertThat(reactionUsers("bookmarks", target.getId()))
                .as("복합 PK 라 단일 UPDATE 면 둘 다 가진 사용자 하나에 전체가 실패합니다")
                .containsExactlyInAnyOrder(userId, otherUserId);
        assertThat(reactionUsers("article_likes", target.getId())).containsExactly(userId);
        assertThat(counter(target.getId(), "bookmark_count"))
                .as("카운터는 트리거가 맞춥니다")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("★ 흡수된 공지의 댓글이 함께 옮겨진다 — 사용자가 남긴 글이 사라지면 안 된다")
    void commentsAreMoved() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        CommentResponse root = commentService.create(source.getId(), userId, "원댓글");
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), null, adminId);
        em.flush();

        assertThat(commentCount(target.getId()))
                .as("사용자가 남긴 댓글이 사라지면 안 됩니다")
                .isEqualTo(1);
        assertThat(counter(target.getId(), "comment_count")).isEqualTo(1);
    }

    @Test
    @DisplayName("분류는 합집합이 된다")
    void categoriesAreUnioned() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        link(target.getId(), categoryId);
        link(source.getId(), categoryId);          // 겹침
        link(source.getId(), secondCategoryId);    // 새로 들어옴
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), null, adminId);
        em.flush();

        assertThat(categoryIdsOf(target.getId()))
                .containsExactlyInAnyOrder(categoryId, secondCategoryId);
    }

    @Test
    @DisplayName("첨부가 옮겨지고, 같은 파일은 중복 제거된다")
    void attachmentsAreMoved() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        attach(target.getId(), "https://inha.ac.kr/files/same.pdf");
        attach(source.getId(), "https://inha.ac.kr/files/same.pdf");   // 같은 파일
        attach(source.getId(), "https://inha.ac.kr/files/other.pdf");  // 새 파일
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), null, adminId);
        em.flush();

        assertThat(attachmentUrls(target.getId()))
                .as("같은 파일을 옮기면 (article_id, md5(file_url)) 유니크에 걸립니다")
                .containsExactlyInAnyOrder(
                        "https://inha.ac.kr/files/same.pdf", "https://inha.ac.kr/files/other.pdf");
    }

    @Test
    @DisplayName("★ 병합 사실이 이력에 남는다 — 안 남기면 나중에 추적할 단서가 없다")
    void mergeIsRecordedInHistory() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        Long sourceId = source.getId();
        em.flush();

        mergeService.merge(target.getId(), List.of(sourceId), "제목만 다른 같은 공지", adminId);
        em.flush();

        assertThat(adminArticleService.statusLogs(target.getId()))
                .extracting(log -> log.memo())
                .anyMatch(memo -> memo != null
                        && memo.contains("#" + sourceId) && memo.contains("제목만 다른 같은 공지"));
    }

    @Test
    @DisplayName("흡수된 공지는 사라진다")
    void sourceArticleIsDeleted() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        Long sourceId = source.getId();
        em.flush();

        mergeService.merge(target.getId(), List.of(sourceId), null, adminId);
        em.flush();
        em.clear();

        assertThat(articleRepository.findById(sourceId)).isEmpty();
    }

    @Test
    @DisplayName("★ 긴 사유를 적어도 병합이 실패하지 않는다")
    void longMemoDoesNotBreakMerge() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        em.flush();

        // 요청이 허용하는 최대 길이. 접두어가 붙으면 memo 컬럼(varchar 500)을 넘깁니다.
        String longMemo = "가".repeat(500);

        mergeService.merge(target.getId(), List.of(source.getId()), longMemo, adminId);
        em.flush();

        // ★ isNotEmpty() 만으로는 아무것도 검증되지 않습니다 — publish() 가 남긴 상태 전이 로그와
        //   흡수된 공지에서 옮겨 온 이력 때문에 recordMerge 가 아무 일도 안 해도 비어 있지 않습니다.
        //   확인해야 할 것은 "잘릴 때 무엇을 살렸는가" 입니다.
        String mergeMemo = adminArticleService.statusLogs(target.getId()).stream()
                .map(StatusLogResponse::memo)
                .filter(memo -> memo != null && memo.contains("#" + source.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("병합 사유가 이력에 없습니다"));

        assertThat(mergeMemo)
                .as("접두어가 잘리면 어느 공지를 흡수했는지가 사라집니다 — "
                        + "잘라야 할 때 버리는 쪽은 사용자 사유여야 합니다")
                .startsWith("공지 #" + source.getId() + " 병합")
                .contains("가")
                .hasSizeLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("★ 병합하면 흡수된 공지를 가리키던 중복 의심 판정이 정리된다")
    void similarityPointingAtSourceIsCleared() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        target.markSimilarTo(new java.math.BigDecimal("88.00"), source.getId());
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), null, adminId);
        em.flush();
        em.clear();

        // FK 가 ON DELETE SET NULL 이라 similar_article_id 만 NULL 이 되고 점수는 남습니다.
        // 그러면 "88% 유사 — 비교 대상 없음" 인 채로 확인 필요 목록에 계속 걸립니다.
        assertThat(scoreOf(target.getId()))
                .as("병합을 끝냈는데도 중복 의심으로 남으면 관리자가 지울 방법이 없습니다")
                .isNull();
    }

    @Test
    @DisplayName("★ 옮겨진 댓글이 '수정됨' 으로 표시되지 않는다")
    void movedCommentsAreNotMarkedEdited() {
        Article target = publish("남을 공지");
        Article source = publish("흡수될 공지");
        CommentResponse comment = commentService.create(source.getId(), userId, "원래 댓글");
        em.flush();

        // 작성 시각을 과거로 밀어 둡니다. now() 가 트랜잭션 고정이라
        // 그러지 않으면 이동으로 updated_at 이 밀렸는지 확인할 수 없습니다.
        em.createNativeQuery("""
                        UPDATE comments SET created_at = now() - interval '1 hour',
                                            updated_at = now() - interval '1 hour'
                         WHERE id = :id
                        """)
                .setParameter("id", comment.id()).executeUpdate();
        em.flush();

        mergeService.merge(target.getId(), List.of(source.getId()), null, adminId);
        em.flush();
        em.clear();

        CommentResponse moved = commentService
                .list(target.getId(), userId, org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent().get(0);

        assertThat(moved.edited())
                .as("관리자가 병합했을 뿐인데 사용자 댓글이 전부 '수정됨' 이 되면 안 됩니다")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 병합 거부 조건
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 출처 유형이 다르면 병합할 수 없다 — 옮기다 말고 뒤집히면 원인을 알 수 없다")
    void cannotMergeAcrossSourceTypes() {
        Article school = publish("학교 공지");
        Article club = articleRepository.saveAndFlush(
                Article.createClubArticle("동아리 공지", "내용", null, null, null));
        club.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();

        assertThatThrownBy(() ->
                mergeService.merge(school.getId(), List.of(club.getId()), null, adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("자기 자신은 흡수할 수 없다")
    void cannotMergeIntoItself() {
        Article article = publish("혼자 있는 공지");
        em.flush();

        assertThatThrownBy(() ->
                mergeService.merge(article.getId(), List.of(article.getId()), null, adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("휴지통에 있는 공지로는 병합할 수 없다")
    void cannotMergeIntoTrashedArticle() {
        Article target = publish("휴지통 갈 공지");
        Article source = publish("흡수될 공지");
        target.changeStatus(ArticleStatus.TRASHED);
        em.flush();

        assertThatThrownBy(() ->
                mergeService.merge(target.getId(), List.of(source.getId()), null, adminId))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-10 영구 삭제
    // ─────────────────────────────────────────────────────────────────────────

    // ADM-10 영구 삭제는 부분 성공이라 건별로 커밋합니다({@code BulkExecutor}).
    // 트랜잭션 테스트 안에서는 그 새 트랜잭션이 준비 데이터를 보지 못하므로
    // 검증을 AdminFileCleanupTest(비트랜잭션)로 옮겼습니다.

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-12 유사 비교
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("유사 공지를 나란히 돌려준다")
    void comparesWithSimilarArticle() {
        Article article = publish("중복 의심 공지");
        Article similar = publish("비교 대상 공지");
        article.markSimilarTo(new java.math.BigDecimal("88.00"), similar.getId());
        em.flush();

        SimilarComparison comparison = mergeService.compareWithSimilar(article.getId());

        assertThat(comparison.article().id()).isEqualTo(article.getId());
        assertThat(comparison.similar().title()).isEqualTo("비교 대상 공지");
        assertThat(comparison.similarityScore()).isEqualByComparingTo("88.00");
    }

    @Test
    @DisplayName("판정되지 않은 공지는 비교 대상이 비어 나간다")
    void comparisonWithoutSimilarArticle() {
        Article article = publish("판정 안 된 공지");
        em.flush();

        assertThat(mergeService.compareWithSimilar(article.getId()).similar())
                .as("아직 판정되지 않았다는 것도 관리자에게 필요한 정보입니다")
                .isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Article publish(String title) {
        Article article = articleRepository.saveAndFlush(
                Article.createSchoolArticle(title, "내용", null, null, null));
        article.changeStatus(ArticleStatus.READY_TO_PUBLISH);
        article.changeStatus(ArticleStatus.PUBLISHED);
        em.flush();
        return article;
    }

    private void crawlerVendor(Long articleId, String externalKey) {
        em.createNativeQuery("""
                        INSERT INTO article_vendors (article_id, vendor_id, source_url, external_key)
                        VALUES (:articleId, :vendorId, 'https://inha.ac.kr/board/' || :key, :key)
                        """)
                .setParameter("articleId", articleId).setParameter("vendorId", vendorId)
                .setParameter("key", externalKey).executeUpdate();
    }

    private void react(String table, Long user, Long articleId) {
        em.createNativeQuery("INSERT INTO " + table + " (user_id, article_id) VALUES (:u, :a)")
                .setParameter("u", user).setParameter("a", articleId).executeUpdate();
    }

    private void link(Long articleId, Long category) {
        em.createNativeQuery("INSERT INTO article_categories (article_id, category_id) VALUES (:a, :c)")
                .setParameter("a", articleId).setParameter("c", category).executeUpdate();
    }

    private void attach(Long articleId, String fileUrl) {
        em.createNativeQuery("""
                        INSERT INTO attachments (article_id, file_url, storage_type)
                        VALUES (:articleId, :fileUrl, 'EXTERNAL')
                        """)
                .setParameter("articleId", articleId).setParameter("fileUrl", fileUrl)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<String> externalKeysOf(Long articleId) {
        return em.createNativeQuery(
                        "SELECT external_key FROM article_vendors WHERE article_id = :id")
                .setParameter("id", articleId).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> reactionUsers(String table, Long articleId) {
        List<Number> rows = em.createNativeQuery(
                        "SELECT user_id FROM " + table + " WHERE article_id = :id")
                .setParameter("id", articleId).getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> categoryIdsOf(Long articleId) {
        List<Number> rows = em.createNativeQuery(
                        "SELECT category_id FROM article_categories WHERE article_id = :id")
                .setParameter("id", articleId).getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> attachmentUrls(Long articleId) {
        return em.createNativeQuery("SELECT file_url FROM attachments WHERE article_id = :id")
                .setParameter("id", articleId).getResultList();
    }

    private int commentCount(Long articleId) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM comments WHERE article_id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }

    private java.math.BigDecimal scoreOf(Long articleId) {
        return (java.math.BigDecimal) em.createNativeQuery(
                        "SELECT similarity_score FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult();
    }

    private int counter(Long articleId, String column) {
        return ((Number) em.createNativeQuery(
                        "SELECT " + column + " FROM articles WHERE id = :id")
                .setParameter("id", articleId).getSingleResult()).intValue();
    }

    private Long insertUser(String email, String role) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '병합테스터', :role, 'ACTIVE')")
                .setParameter("email", email).setParameter("role", role).executeUpdate();
        return firstId("SELECT id FROM users WHERE email = '" + email + "'");
    }

    private Long insertVendor(String name, String initial) {
        em.createNativeQuery("INSERT INTO vendors (name, initial, type) VALUES (:name, :initial, 'SCHOOL')")
                .setParameter("name", name).setParameter("initial", initial).executeUpdate();
        return firstId("SELECT id FROM vendors WHERE initial = '" + initial + "'");
    }

    private Long firstId(String sql) {
        return ((Number) em.createNativeQuery(sql + " LIMIT 1").getSingleResult()).longValue();
    }
}
