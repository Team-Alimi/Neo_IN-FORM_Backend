package today.inform.inform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest.AttachmentLink;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.service.AdminArticleWriteService;
import today.inform.inform.admin.file.dto.response.UploadedFileResponse;
import today.inform.inform.admin.file.service.AdminFileService;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.support.FakeFileStorage;
import today.inform.inform.support.IntegrationTest;

/**
 * FIL-01 업로드 · FIL-02 취소 · 공지 연결 · 영구 삭제 시 정리.
 *
 * <p><b>v1 이 여기서 실패했습니다.</b> 삭제 코드는 있었는데 <b>어디서도 불리지 않아</b>
 * 공지를 지워도 스토리지 객체가 그대로 남았습니다. 그래서 이 파일의 절반은
 * "삭제가 실제로 불리는가" 를 확인하는 데 씁니다 — 코드의 존재가 아니라 호출을 검증합니다.
 */
@Transactional
@Import(FakeFileStorage.Config.class)
class AdminFileTest extends IntegrationTest {

    @Autowired
    private AdminFileService fileService;

    @Autowired
    private AdminArticleWriteService writeService;

    @Autowired
    private FakeFileStorage storage;

    @PersistenceContext
    private EntityManager em;

    private Long adminId;

    @BeforeEach
    void setUp() {
        storage.reset();
        adminId = insertUser("file-admin@inha.ac.kr");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIL-01 업로드
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이미지를 올리면 주소가 돌아오고, DB 에는 아무것도 생기지 않는다")
    void uploadReturnsUrlWithoutTouchingDatabase() {
        List<UploadedFileResponse> uploaded = fileService.upload(List.of(image("poster.png")));

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).fileUrl()).startsWith(FakeFileStorage.BASE_URL);
        assertThat(uploaded.get(0).originalName()).isEqualTo("poster.png");
        assertThat(attachmentCount())
                .as("연결은 공지 저장 시점입니다. 여기서 행을 만들면 주인 없는 첨부가 생깁니다")
                .isZero();
    }

    @Test
    @DisplayName("★ 클라이언트가 보낸 Content-Type 을 믿지 않는다 — 확장자로 다시 정한다")
    void contentTypeComesFromExtensionNotFromClient() {
        MockMultipartFile disguised = new MockMultipartFile(
                "files", "innocent.png", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThat(fileService.upload(List.of(disguised)).get(0).contentType())
                .as("그대로 저장하면 버킷 도메인이 HTML 로 서빙해 저장형 XSS 가 됩니다")
                .isEqualTo("image/png");
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 거부한다")
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() -> fileService.upload(List.of(image("payload.svg"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileService.upload(List.of(empty)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_IS_EMPTY);
    }

    @Test
    @DisplayName("★ 한 건이라도 규격 위반이면 아무것도 올라가지 않는다")
    void oneBadFileRejectsTheWholeBatch() {
        assertThatThrownBy(() -> fileService.upload(List.of(image("ok.png"), image("bad.exe"))))
                .isInstanceOf(BusinessException.class);

        assertThat(storage.remaining())
                .as("올리면서 검사하면 앞의 파일은 이미 올라간 채로 400 이 나가 고아가 됩니다")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIL-02 취소
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("연결되지 않은 파일은 지워진다")
    void deletesUnlinkedFile() {
        String url = fileService.upload(List.of(image("draft.png"))).get(0).fileUrl();

        assertThat(fileService.deleteUnlinked(List.of(url))).isEqualTo(1);
        assertThat(storage.remaining()).isZero();
    }

    @Test
    @DisplayName("★ 이미 공지에 연결된 파일은 거부한다 — 지우면 공지에 깨진 링크만 남는다")
    void refusesToDeleteLinkedFile() {
        String url = fileService.upload(List.of(image("used.png"))).get(0).fileUrl();
        createArticleWith(url);
        em.flush();

        assertThatThrownBy(() -> fileService.deleteUnlinked(List.of(url)))
                .isInstanceOf(BusinessException.class);

        assertThat(storage.deletedKeys())
                .as("거부했으면 아무것도 지우지 않아야 합니다")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 연결된 파일이 하나라도 섞이면 나머지도 지우지 않는다")
    void mixedBatchIsRejectedEntirely() {
        String linked = fileService.upload(List.of(image("linked.png"))).get(0).fileUrl();
        String free = fileService.upload(List.of(image("free.png"))).get(0).fileUrl();
        createArticleWith(linked);
        em.flush();

        assertThatThrownBy(() -> fileService.deleteUnlinked(List.of(linked, free)))
                .isInstanceOf(BusinessException.class);

        assertThat(storage.deletedKeys())
                .as("일부만 지우면 관리자는 200 을 받고 나머지가 남은 줄 모릅니다")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 우리 스토리지 주소가 아니면 건너뛴다 — 남의 주소로 우리 객체를 지울 수 없다")
    void ignoresForeignUrls() {
        assertThat(fileService.deleteUnlinked(List.of("https://evil.example.com/2026/08/secret.png")))
                .isZero();
        assertThat(storage.deletedKeys()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 공지 연결
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 우리 스토리지 파일은 S3 로, 남의 주소는 EXTERNAL 로 저장된다")
    void storageTypeIsDecidedByTheServer() {
        String ours = fileService.upload(List.of(image("ours.png"))).get(0).fileUrl();
        String theirs = "https://inha.ac.kr/board/original.pdf";

        Long articleId = createArticleWith(ours, theirs);
        em.flush();

        assertThat(storageTypeOf(articleId, ours))
                .as("클라이언트가 정하게 두면 남의 주소를 S3 로 등록해 두었다가 "
                        + "공지를 지울 때 우리 버킷의 엉뚱한 객체를 지우게 만들 수 있습니다")
                .isEqualTo("S3");
        assertThat(storageTypeOf(articleId, theirs)).isEqualTo("EXTERNAL");
        assertThat(objectKeyOf(articleId, theirs))
                .as("ck_attachments_object_key 가 EXTERNAL 이면 NULL 이어야 한다고 강제합니다")
                .isNull();
    }

    @Test
    @DisplayName("★ 첨부를 빈 배열로 저장하면 기존 첨부가 지워진다 (전체 교체)")
    void savingWithEmptyAttachmentsClearsThem() {
        String url = fileService.upload(List.of(image("first.png"))).get(0).fileUrl();
        Long articleId = createArticleWith(url);
        em.flush();
        assertThat(attachmentCount()).isEqualTo(1);

        writeService.update(articleId, request(List.of()));
        em.flush();

        assertThat(attachmentCount()).isZero();
    }

    @Test
    @DisplayName("★ 첨부 목록을 생략하면 기존 첨부가 유지된다 — 생략과 빈 배열은 다른 뜻이다")
    void omittingAttachmentsKeepsThem() {
        String url = fileService.upload(List.of(image("keep.png"))).get(0).fileUrl();
        Long articleId = createArticleWith(url);
        em.flush();

        // 제목만 고치는 요청. 첨부·분류·출처를 안 보냅니다.
        writeService.update(articleId, new SaveArticleRequest(
                null, null, null, "제목만 고침", "본문", null, null, null, null, null));
        em.flush();

        assertThat(attachmentCount())
                .as("생략이 곧 삭제면 제목 오타 하나 고치려다 첨부가 통째로 사라집니다")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("files", name, "image/png", "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private Long createArticleWith(String... fileUrls) {
        List<AttachmentLink> links = java.util.Arrays.stream(fileUrls)
                .map(url -> new AttachmentLink(null, url, "file", "image/png", 100L))
                .toList();
        Long created = writeService.create(request(links), adminId);
        em.flush();
        return created;
    }

    private SaveArticleRequest request(List<AttachmentLink> attachments) {
        return new SaveArticleRequest(
                null, SourceType.SCHOOL, null, "첨부 테스트 공지", "본문",
                null, null, List.of(), List.of(), attachments);
    }

    private int attachmentCount() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM attachments").getSingleResult()).intValue();
    }

    private String storageTypeOf(Long articleId, String fileUrl) {
        return (String) em.createNativeQuery(
                        "SELECT storage_type FROM attachments WHERE article_id = :a AND file_url = :u")
                .setParameter("a", articleId).setParameter("u", fileUrl).getSingleResult();
    }

    private Object objectKeyOf(Long articleId, String fileUrl) {
        return em.createNativeQuery(
                        "SELECT object_key FROM attachments WHERE article_id = :a AND file_url = :u")
                .setParameter("a", articleId).setParameter("u", fileUrl).getSingleResult();
    }

    private Long insertUser(String email) {
        em.createNativeQuery("INSERT INTO users (email, name, role, status) "
                        + "VALUES (:email, '파일테스터', 'ADMIN', 'ACTIVE')")
                .setParameter("email", email).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE email = :email")
                .setParameter("email", email).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("여러 건을 한 번에 올린다")
    void uploadsMultipleFiles() {
        assertThatCode(() -> fileService.upload(List.of(image("a.png"), image("b.jpg"), image("c.webp"))))
                .doesNotThrowAnyException();
        assertThat(storage.remaining()).isEqualTo(3);
    }
}
