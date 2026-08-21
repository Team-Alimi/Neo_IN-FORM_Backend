package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest.AttachmentLink;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.article.service.AdminArticleWriteService;
import today.inform.inform.admin.article.service.ArticleMergeService;
import today.inform.inform.admin.file.service.AdminFileService;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.support.FakeFileStorage;
import today.inform.inform.support.IntegrationTest;

/**
 * ADM-10 영구 삭제가 스토리지 객체까지 지우는지.
 *
 * <p><b>v1 이 정확히 여기서 실패했습니다.</b> 삭제 코드는 있었지만 <b>어디서도 불리지 않아</b>
 * 공지를 지워도 스토리지에 파일이 그대로 남았습니다. 그래서 검증 대상은
 * "코드가 있는가" 가 아니라 <b>"실제로 불리는가"</b> 입니다.
 *
 * <p><b>{@code @Transactional} 이 없습니다 — 그게 이 파일이 따로 있는 이유입니다.</b>
 * S3 삭제는 커밋 이후로 미뤄집니다({@code afterCommit}). 롤백되는 테스트 트랜잭션 안에서는
 * 그 콜백이 <b>영원히 실행되지 않아</b>, 삭제를 통째로 지워도 테스트가 통과합니다.
 */
@Import(FakeFileStorage.Config.class)
class AdminFileCleanupTest extends IntegrationTest {

    private static final String EMAIL = "file-cleanup@inha.ac.kr";
    private static final String TITLE = "첨부 정리 테스트 공지";
    private static final String EXTERNAL_URL = "https://inha.ac.kr/board/original.pdf";

    @Autowired
    private AdminFileService fileService;

    @Autowired
    private AdminArticleWriteService writeService;

    @Autowired
    private ArticleMergeService mergeService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private FakeFileStorage storage;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;

    @BeforeEach
    void setUp() {
        cleanUp();
        storage.reset();
        transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                                + "VALUES (:e, '정리테스터', 'ADMIN', 'ACTIVE')")
                        .setParameter("e", EMAIL).executeUpdate());
        adminId = transactionTemplate.execute(status ->
                ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :e")
                        .setParameter("e", EMAIL).getSingleResult()).longValue());
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM articles WHERE title = :t")
                    .setParameter("t", TITLE).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE email = :e")
                    .setParameter("e", EMAIL).executeUpdate();
        });
    }

    @Test
    @DisplayName("★ 공지를 영구 삭제하면 S3 객체도 지워진다 — v1 이 여기서 실패했다")
    void permanentDeleteRemovesS3Objects() {
        String ours = transactionTemplate.execute(status ->
                fileService.upload(List.of(image("attached.png"))).get(0).fileUrl());
        String key = storage.objectKeyOf(ours).orElseThrow();

        Long articleId = createTrashedArticleWith(ours, EXTERNAL_URL);

        transactionTemplate.executeWithoutResult(status ->
                mergeService.deletePermanently(List.of(articleId)));

        assertThat(storage.deletedKeys())
                .as("attachments 는 CASCADE 라 공지를 지우는 순간 어떤 객체를 지울지 알 방법이 사라집니다. "
                        + "반드시 지우기 전에 읽어 두어야 합니다")
                .containsExactly(key);
    }

    @Test
    @DisplayName("원본 사이트 첨부(EXTERNAL)는 지우려 하지 않는다 — 우리 파일이 아니다")
    void externalAttachmentsAreNotDeleted() {
        Long articleId = createTrashedArticleWith(EXTERNAL_URL);

        transactionTemplate.executeWithoutResult(status ->
                mergeService.deletePermanently(List.of(articleId)));

        assertThat(storage.deletedKeys())
                .as("원본 사이트의 파일까지 지우려 들면 우리 것이 아닌 자원에 손대는 것입니다")
                .isEmpty();
    }

    /**
     * <b>이 테스트가 검증하지 <i>않는</i> 것</b> — {@code deleteObjectsAfterCommit} 의 지연 자체.
     *
     * <p>여기서 만드는 실패(NOT_IN_TRASH)는 객체 키를 <b>읽기도 전에</b> 던져지므로,
     * 삭제 대상 목록이 애초에 만들어지지 않습니다. 즉 아무것도 안 지워진 이유는 "지연" 이 아니라
     * "지울 것이 없었기" 때문입니다. 지연을 즉시 삭제로 바꿔도 이 테스트는 통과합니다.
     *
     * <p>지연을 직접 고정하려 했지만 지금 구조에서는 관찰할 방법이 없습니다 —
     * 키를 읽은 <b>뒤에</b> 실패하는 경로가 하나도 없고
     * ({@code attachments} 는 CASCADE 라 삭제가 FK 로 막히지 않습니다),
     * {@code afterCommit} 중에도 {@code isActualTransactionActive()} 가 참이라
     * 호출 시점으로도 구분되지 않습니다.
     *
     * <p>그래서 지연은 <b>테스트가 아니라 코드 리뷰로 지켜지는 성질</b>입니다.
     * 키를 읽은 뒤 실패할 수 있는 단계가 생기면 그때 이 테스트를 실제 검증으로 바꿔야 합니다.
     */
    @Test
    @DisplayName("휴지통이 아니면 거부되고, 그때는 지울 객체를 계산하지도 않는다")
    void rejectedDeleteTouchesNoObjects() {
        String ours = transactionTemplate.execute(status ->
                fileService.upload(List.of(image("kept.png"))).get(0).fileUrl());

        // 휴지통에 넣지 않은 공지. 영구 삭제가 거부됩니다.
        Long articleId = createArticleWith(ours);

        BulkResult result = mergeService.deletePermanently(List.of(articleId));

        assertThat(result.failed())
                .singleElement()
                .satisfies(failure -> assertThat(failure.code()).isEqualTo("NOT_IN_TRASH"));
        assertThat(storage.deletedKeys())
                .as("거부된 건의 첨부에는 손대지 않아야 합니다")
                .isEmpty();
        assertThat(articleRepository.findById(articleId)).isPresent();
    }

    @Test
    @DisplayName("휴지통에 없는 공지는 영구 삭제되지 않는다")
    void onlyTrashedArticlesAreDeleted() {
        Long live = createArticleWith();
        Long trashed = createTrashedArticleWith();

        BulkResult result = mergeService.deletePermanently(List.of(live, trashed));

        assertThat(result.succeeded()).containsExactly(trashed);
        assertThat(result.failed()).extracting(BulkResult.Failure::id).containsExactly(live);
        assertThat(articleRepository.findById(live))
                .as("목록에서 잘못 선택한 공지가 곧바로 사라지면 되돌릴 수 없습니다")
                .isPresent();
        assertThat(articleRepository.findById(trashed)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Long createTrashedArticleWith(String... fileUrls) {
        Long id = createArticleWith(fileUrls);
        transactionTemplate.executeWithoutResult(status ->
                articleRepository.findById(id).orElseThrow().changeStatus(ArticleStatus.TRASHED));
        return id;
    }

    private Long createArticleWith(String... fileUrls) {
        List<AttachmentLink> links = java.util.Arrays.stream(fileUrls)
                .map(url -> new AttachmentLink(null, url, "file", "image/png", 100L))
                .toList();

        return transactionTemplate.execute(status -> writeService.create(new SaveArticleRequest(
                null, SourceType.SCHOOL, null, TITLE, "본문",
                null, null, List.of(), List.of(), links), adminId));
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(
                "files", name, "image/png", "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
    }
}
