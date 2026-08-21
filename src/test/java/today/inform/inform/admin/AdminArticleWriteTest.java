package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest.VendorLink;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.service.AdminArticleWriteService;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.support.IntegrationTest;

/**
 * ADM-04 · 05 · 06 — 관리자 작성·수정.
 *
 * <p>여기서 확인해야 할 것은 <b>나중에 터지는 것들</b>입니다.
 * 번호를 수동 지정하고 시퀀스를 안 밀면 몇 달 뒤 크롤러가 죽고,
 * 출처를 통째로 교체하면 다음 수집에서 공지가 복제됩니다.
 * 둘 다 저장하는 순간에는 아무 증상이 없습니다.
 */
@Transactional
class AdminArticleWriteTest extends IntegrationTest {

    @Autowired
    private AdminArticleWriteService writeService;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;
    private Long vendorId;
    private Long categoryId;
    private Long secondCategoryId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("write-admin@inha.ac.kr");
        vendorId = insertVendor("작성테스트학과", "WRITETEST");
        categoryId = firstId("SELECT id FROM categories ORDER BY id");
        secondCategoryId = firstId("SELECT id FROM categories ORDER BY id OFFSET 1");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-06 생성
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("번호를 비우면 시퀀스가 발급하고, 카테고리·출처가 함께 저장된다")
    void createWithGeneratedId() {
        Long created = writeService.create(request(null, null, "새 공지"), adminId);
        em.flush();

        assertThat(created).isNotNull();
        assertThat(writeService.getDetail(created).status()).isEqualTo(ArticleStatus.PENDING_REVIEW);
        assertThat(writeService.getDetail(created).createdBy()).isEqualTo(adminId);
        assertThat(writeService.getDetail(created).categories()).hasSize(2);
        assertThat(writeService.getDetail(created).vendors()).extracting(AdminArticleDetail.VendorRef::sourceUrl)
                .containsExactly("https://example.inha.ac.kr/1");
    }

    @Test
    @DisplayName("★ 번호를 직접 지정하면 시퀀스가 그 뒤로 밀린다 — 안 밀면 나중에 크롤러가 죽는다")
    void manualIdBumpsSequence() {
        long manualId = currentSequenceValue() + 500;

        writeService.create(request(manualId, null, "번호 지정 공지"), adminId);
        em.flush();

        long next = nextSequenceValue();
        assertThat(next)
                .as("시퀀스가 지정 번호보다 뒤에 있어야 크롤러가 그 번호에 부딪히지 않습니다")
                .isGreaterThan(manualId);
    }

    @Test
    @DisplayName("작은 번호를 지정해도 시퀀스가 뒤로 돌아가지 않는다")
    void manualIdDoesNotRewindSequence() {
        writeService.create(request(null, null, "먼저 만든 공지"), adminId);
        em.flush();
        long before = currentSequenceValue();

        // 확실히 비어 있는 작은 번호를 고릅니다. 하드코딩하면 그 번호가 이미 쓰였을 때
        // 시퀀스와 무관한 이유(409)로 실패해 무엇을 검증하는지 알 수 없게 됩니다.
        long freeSmallId = firstFreeIdBelow(before);
        writeService.create(request(freeSmallId, null, "작은 번호 공지"), adminId);
        em.flush();

        assertThat(currentSequenceValue())
                .as("되돌리면 이미 쓰인 번호를 다시 발급하게 됩니다")
                .isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("★ 이미 쓰는 번호를 지정하면 409 로 거부된다")
    void duplicateManualIdIsRejected() {
        Long existing = writeService.create(request(null, null, "기존 공지"), adminId);
        em.flush();

        assertThatThrownBy(() -> {
            writeService.create(request(existing, null, "같은 번호 공지"), adminId);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("초기 상태를 지정해 만들 수 있다 — 생성은 전이가 아니다")
    void createWithExplicitStatus() {
        Long created = writeService.create(
                request(null, ArticleStatus.PUBLISHED, "바로 배포할 공지"), adminId);
        em.flush();

        assertThat(writeService.getDetail(created).status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(writeService.getDetail(created).publishedAt())
                .as("발행 시각은 V6 트리거가 채웁니다")
                .isNotNull();
    }

    @Test
    @DisplayName("출처 유형에 없는 상태로는 만들 수 없다")
    void statusMustMatchSourceType() {
        SaveArticleRequest invalid = new SaveArticleRequest(
                null, SourceType.SCHOOL, ArticleStatus.DRAFT, "제목", "본문",
                null, null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> writeService.create(invalid, adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("번호를 지정한 경로에서도 기간 역전이 막힌다")
    void manualIdPathValidatesPeriod() {
        SaveArticleRequest invalid = new SaveArticleRequest(
                currentSequenceValue() + 900, SourceType.SCHOOL, null, "제목", "본문",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> writeService.create(invalid, adminId))
                .as("native INSERT 경로는 엔티티 검증을 안 거치므로 따로 막아야 합니다")
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-05 수정
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 수정이 크롤러가 수집한 출처를 지우지 않는다 — 지우면 다음 수집에서 공지가 복제된다")
    void updateKeepsCrawlerCollectedVendors() {
        Long created = writeService.create(request(null, null, "수집분이 붙은 공지"), adminId);
        em.flush();

        // 크롤러가 넣은 출처(원본 게시판 글 번호가 있음)
        em.createNativeQuery("""
                        INSERT INTO article_vendors (article_id, vendor_id, source_url, external_key)
                        VALUES (:articleId, :vendorId, 'https://inha.ac.kr/board/9999', '9999')
                        """)
                .setParameter("articleId", created).setParameter("vendorId", vendorId)
                .executeUpdate();
        em.flush();

        // 관리자가 제목만 고쳐 저장합니다. 출처 목록에는 수기 추가분만 담겨 옵니다.
        writeService.update(created, request(null, null, "제목만 고침"));
        em.flush();

        List<AdminArticleDetail.VendorRef> vendors = writeService.getDetail(created).vendors();

        assertThat(vendors)
                .as("수집분이 사라지면 크롤러가 처음 보는 글로 인식해 공지를 새로 만듭니다")
                .extracting(AdminArticleDetail.VendorRef::externalKey)
                .contains("9999");
        assertThat(vendors).hasSize(2);   // 수집분 + 수기 추가분
    }

    @Test
    @DisplayName("수정하면 카테고리가 최종 목록으로 맞춰진다")
    void updateReplacesCategories() {
        Long created = writeService.create(request(null, null, "분류 바꿀 공지"), adminId);
        em.flush();
        assertThat(writeService.getDetail(created).categories()).hasSize(2);

        SaveArticleRequest single = new SaveArticleRequest(
                null, null, null, "분류 바꿀 공지", "본문", null, null,
                List.of(categoryId), List.of(), List.of());
        writeService.update(created, single);
        em.flush();

        assertThat(writeService.getDetail(created).categories()).hasSize(1);
    }

    @Test
    @DisplayName("★ 수정은 상태를 바꾸지 않는다 — 상태는 전이 규칙을 타야 한다")
    void updateDoesNotChangeStatus() {
        Long created = writeService.create(request(null, null, "상태 유지 공지"), adminId);
        em.flush();

        SaveArticleRequest tryingToPublish = new SaveArticleRequest(
                null, SourceType.CLUB, ArticleStatus.PUBLISHED, "상태 유지 공지", "본문",
                null, null, List.of(), List.of(), List.of());
        writeService.update(created, tryingToPublish);
        em.flush();

        AdminArticleDetail after = writeService.getDetail(created);
        assertThat(after.status())
                .as("수정으로 검수를 건너뛸 수 있으면 상태 머신이 무의미해집니다")
                .isEqualTo(ArticleStatus.PENDING_REVIEW);
        assertThat(after.sourceType())
                .as("출처 유형도 바뀌면 안 됩니다")
                .isEqualTo(SourceType.SCHOOL);
    }

    @Test
    @DisplayName("본문을 고치면 AI 요약이 무효화된다")
    void updateInvalidatesSummary() {
        Long created = writeService.create(request(null, null, "요약 붙은 공지"), adminId);
        em.flush();
        em.createNativeQuery("UPDATE articles SET summary = 'AI 요약' WHERE id = :id")
                .setParameter("id", created).executeUpdate();
        em.flush();
        em.clear();

        SaveArticleRequest edited = new SaveArticleRequest(
                null, null, null, "요약 붙은 공지", "본문을 완전히 바꿉니다", null, null,
                List.of(), List.of(), List.of());
        writeService.update(created, edited);
        em.flush();

        assertThat(writeService.getDetail(created).summary())
                .as("트리거가 지웁니다. 앱이 따로 할 일이 없습니다")
                .isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 리뷰가 지적한 데이터 유실 경로
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 상세에서 받은 출처를 그대로 되돌려 보내도 행이 늘어나지 않는다")
    void roundTrippingVendorsDoesNotDuplicate() {
        Long created = writeService.create(request(null, null, "왕복 저장 공지"), adminId);
        em.flush();
        em.createNativeQuery("""
                        INSERT INTO article_vendors (article_id, vendor_id, source_url, external_key)
                        VALUES (:articleId, :vendorId, 'https://inha.ac.kr/board/1234', '1234')
                        """)
                .setParameter("articleId", created).setParameter("vendorId", vendorId)
                .executeUpdate();
        em.flush();
        em.clear();

        // 화면이 상세 응답을 그대로 폼에 담아 되돌려 보냅니다.
        for (int i = 0; i < 3; i++) {
            AdminArticleDetail current = writeService.getDetail(created);
            writeService.update(created, echo(current));
            em.flush();
            em.clear();
        }

        assertThat(writeService.getDetail(created).vendors())
                .as("저장할 때마다 한 줄씩 늘어나면 사용자 화면에 같은 학과가 여러 번 뜹니다")
                .hasSize(2);
    }

    @Test
    @DisplayName("★ 분류·출처를 빠뜨리면 그대로 유지된다 — 생략과 빈 배열은 다른 뜻이다")
    void omittingCollectionsKeepsThem() {
        Long created = writeService.create(request(null, null, "부분 수정 대상"), adminId);
        em.flush();
        assertThat(writeService.getDetail(created).categories()).hasSize(2);

        SaveArticleRequest titleOnly = new SaveArticleRequest(
                null, null, null, "제목만 고침", "본문", null, null, null, null, null);
        writeService.update(created, titleOnly);
        em.flush();

        assertThat(writeService.getDetail(created).categories())
                .as("보내지 않은 것이 '지워 달라' 로 해석되면 안 됩니다")
                .hasSize(2);
        assertThat(writeService.getDetail(created).vendors()).isNotEmpty();
    }

    @Test
    @DisplayName("★ 제목만 고쳐도 기간이 지워지지 않는다 — 명세: 보낸 필드만 반영")
    void patchKeepsFieldsThatWereNotSent() {
        Long created = writeService.create(new SaveArticleRequest(
                null, SourceType.SCHOOL, null, "기간 있는 공지", "본문",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(), List.of(), List.of()), adminId);
        em.flush();

        // 제목만 보냅니다. 기간·본문·목록은 전부 생략.
        writeService.update(created, new SaveArticleRequest(
                null, null, null, "제목만 고침", null, null, null, null, null, null));
        em.flush();
        em.clear();

        AdminArticleDetail after = writeService.getDetail(created);
        assertThat(after.title()).isEqualTo("제목만 고침");
        assertThat(after.startsOn())
                .as("생략한 기간이 지워지면 오류 없이 사라져 관리자가 알아채지 못합니다")
                .isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(after.endsOn()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(after.content())
                .as("본문도 마찬가지입니다")
                .isEqualTo("본문");
    }

    @Test
    @DisplayName("생성에는 제목·본문이 필수다 — DTO 가 아니라 서비스가 막는다")
    void createStillRequiresTitleAndContent() {
        SaveArticleRequest noTitle = new SaveArticleRequest(
                null, SourceType.SCHOOL, null, null, "본문",
                null, null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> writeService.create(noTitle, adminId))
                .as("PATCH 가 같은 DTO 를 쓰므로 @NotBlank 를 걸 수 없습니다")
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 빈 배열을 명시하면 지워진다 — 생략과 구분되어야 한다")
    void explicitEmptyListClearsThem() {
        Long created = writeService.create(request(null, null, "비울 대상"), adminId);
        em.flush();

        writeService.update(created, new SaveArticleRequest(
                null, null, null, "비움", "본문", null, null, List.of(), List.of(), List.of()));
        em.flush();

        assertThat(writeService.getDetail(created).categories()).isEmpty();
    }

    @Test
    @DisplayName("★ 휴지통 상태로는 만들 수 없다 — 복구할 직전 상태가 없어 영영 못 꺼낸다")
    void cannotCreateDirectlyInTrash() {
        SaveArticleRequest trashed = new SaveArticleRequest(
                null, SourceType.SCHOOL, ArticleStatus.TRASHED, "휴지통에서 태어난 공지", "본문",
                null, null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> writeService.create(trashed, adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ 터무니없이 큰 번호는 거부한다 — setval 이 시퀀스를 소진시키면 되돌릴 수 없다")
    void absurdlyLargeIdIsRejected() {
        SaveArticleRequest huge = new SaveArticleRequest(
                Long.MAX_VALUE / 2, SourceType.SCHOOL, null, "거대 번호", "본문",
                null, null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> writeService.create(huge, adminId))
                .isInstanceOf(BusinessException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADM-04 상세 / 중복 확인
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("관리자 상세는 검수 대기 공지도 열린다")
    void detailOpensUnpublishedArticle() {
        Long created = writeService.create(request(null, null, "미배포 공지"), adminId);
        em.flush();

        assertThat(writeService.getDetail(created).status())
                .isEqualTo(ArticleStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("없는 공지 상세는 404 다")
    void detailNotFound() {
        assertThatThrownBy(() -> writeService.getDetail(999_999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("번호 중복 확인")
    void checkId() {
        Long created = writeService.create(request(null, null, "번호 확인용"), adminId);
        em.flush();

        assertThat(writeService.isIdAvailable(created)).isFalse();
        assertThat(writeService.isIdAvailable(currentSequenceValue() + 10_000)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 화면이 상세 응답을 그대로 폼에 담아 되돌려 보내는 흐름을 흉내 냅니다. */
    private SaveArticleRequest echo(AdminArticleDetail detail) {
        return new SaveArticleRequest(
                null, null, null, detail.title(), detail.content(),
                detail.startsOn(), detail.endsOn(),
                detail.categories().stream().map(AdminArticleDetail.CategoryRef::id).toList(),
                detail.vendors().stream()
                        .map(v -> new VendorLink(v.id(), v.vendorId(), v.sourceUrl()))
                        .toList(), List.of());
    }

    private SaveArticleRequest request(Long articleId, ArticleStatus status, String title) {
        return new SaveArticleRequest(
                articleId, SourceType.SCHOOL, status, title, "본문입니다. 충분히 깁니다.",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(categoryId, secondCategoryId),
                List.of(new VendorLink(null, vendorId, "https://example.inha.ac.kr/1")), List.of());
    }

    /** {@code ceiling} 아래에서 아직 쓰이지 않은 번호. */
    private long firstFreeIdBelow(long ceiling) {
        return ((Number) em.createNativeQuery("""
                        SELECT g FROM generate_series(1, :ceiling) AS g
                         WHERE NOT EXISTS (SELECT 1 FROM articles a WHERE a.id = g)
                         ORDER BY g LIMIT 1
                        """)
                .setParameter("ceiling", ceiling).getSingleResult()).longValue();
    }

    /** 현재 시퀀스 값. 아직 한 번도 안 썼으면 1 입니다. */
    private long currentSequenceValue() {
        return ((Number) em.createNativeQuery("SELECT last_value FROM articles_id_seq")
                .getSingleResult()).longValue();
    }

    /** 다음에 발급될 값. 확인용이라 시퀀스를 실제로 소비합니다. */
    private long nextSequenceValue() {
        return ((Number) em.createNativeQuery("SELECT nextval('articles_id_seq')")
                .getSingleResult()).longValue();
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '작성관리자', 'ADMIN', 'ACTIVE')")
                .setParameter("email", email).executeUpdate();
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
