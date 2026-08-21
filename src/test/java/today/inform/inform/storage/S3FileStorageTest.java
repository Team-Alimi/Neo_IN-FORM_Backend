package today.inform.inform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@code S3FileStorage} 자체 — <b>Spring 도 Docker 도 안 씁니다.</b>
 *
 * <h2>왜 이 파일이 필요한가</h2>
 * 통합 테스트는 {@code FakeFileStorage} 대역을 씁니다. 그런데 키 형식·URL 조립·
 * Content-Type 재판정·주소 접두어 가드가 <b>대역과 실제 구현에 각각 중복 구현</b>돼 있어서,
 * 대역만 태우면 <b>실제 구현을 통째로 망가뜨려도 전부 통과합니다.</b>
 *
 * <p>실제로 그런 상태였습니다. 리뷰가 두 가지를 확인했습니다 —
 * {@code S3FileStorage} 의 Content-Type 을 클라이언트 값으로 되돌려도,
 * {@code objectKeyOf} 의 접두어 가드를 지워도 246개 테스트가 전부 초록불이었습니다.
 * 둘 다 코드 주석이 "이걸 빼면 이런 사고가 난다" 고 적어 둔 방어였습니다.
 *
 * <h2>왜 S3Client 를 흉내 내는가</h2>
 * 자격증명 없이 돌아야 하고, LocalStack 을 붙이면 모든 테스트가 컨테이너를 하나 더 기다립니다.
 * 확인하고 싶은 것은 <b>우리가 S3 에 무엇을 보내는가</b>이지 S3 가 그것을 어떻게 저장하는가가 아닙니다.
 * 그래서 요청만 받아 적는 가짜 클라이언트를 씁니다.
 */
class S3FileStorageTest {

    private static final String BUCKET = "inform-test-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final String BASE = "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/";

    private final RecordingS3Client s3 = new RecordingS3Client();
    private final S3FileStorage storage = new S3FileStorage(BUCKET, REGION, () -> s3);

    // ─────────────────────────────────────────────────────────────────────────
    // 업로드
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 클라이언트가 보낸 Content-Type 을 S3 에 그대로 넘기지 않는다 — 저장형 XSS 경로")
    void contentTypeSentToS3ComesFromTheExtension() {
        MockMultipartFile disguised = new MockMultipartFile(
                "files", "innocent.png", "text/html",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        StoredFile stored = storage.upload(disguised);

        assertThat(s3.puts).hasSize(1);
        assertThat(s3.puts.get(0).contentType())
                .as("클라이언트 값을 그대로 저장하면 버킷 도메인이 HTML 로 서빙합니다. "
                        + "그 도메인은 우리 것이고, 링크는 공지 본문에 박혀 나갑니다")
                .isEqualTo("image/png");
        assertThat(stored.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("★ 객체 키에 원본 파일명이 들어가지 않는다 — 확장자만 화이트리스트에서 가져온다")
    void objectKeyNeverContainsTheOriginalFilename() {
        StoredFile stored = storage.upload(new MockMultipartFile(
                "files", "../../비밀 문서 (1).PNG", "image/png", "x".getBytes(StandardCharsets.UTF_8)));

        String key = s3.puts.get(0).key();
        assertThat(key)
                .as("사용자 문자열을 키에 이어 붙이면 경로 탈출·덮어쓰기·인코딩 사고가 한꺼번에 열립니다")
                .doesNotContain("비밀", "..", " ", "(1)")
                .startsWith(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/")
                .endsWith(".png");
        assertThat(stored.objectKey()).isEqualTo(key);
        assertThat(stored.originalName())
                .as("원본 이름은 화면 표시용으로 따로 남습니다")
                .isEqualTo("../../비밀 문서 (1).PNG");
    }

    @Test
    @DisplayName("같은 이름을 두 번 올려도 서로 덮어쓰지 않는다")
    void twoUploadsOfTheSameNameGetDifferentKeys() {
        storage.upload(image("poster.png"));
        storage.upload(image("poster.png"));

        assertThat(s3.puts.get(0).key()).isNotEqualTo(s3.puts.get(1).key());
    }

    @Test
    @DisplayName("올린 주소가 버킷·리전과 맞고, 되짚으면 같은 키가 나온다")
    void urlAndKeyRoundTrip() {
        StoredFile stored = storage.upload(image("poster.png"));

        assertThat(stored.fileUrl()).isEqualTo(BASE + stored.objectKey());
        assertThat(storage.objectKeyOf(stored.fileUrl())).contains(stored.objectKey());
    }

    @Test
    @DisplayName("업로드 크기를 S3 에 정확히 알려 준다")
    void contentLengthIsSent() {
        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        storage.upload(new MockMultipartFile("files", "a.png", "image/png", bytes));

        assertThat(s3.puts.get(0).contentLength()).isEqualTo(bytes.length);
        assertThat(s3.puts.get(0).bucket()).isEqualTo(BUCKET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 주소 → 키 (삭제가 이걸 믿습니다)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 우리 버킷 주소가 아니면 키를 내주지 않는다 — 남의 주소로 우리 객체를 지울 수 없다")
    void foreignUrlsYieldNoKey() {
        assertThat(storage.objectKeyOf("https://evil.example.com/2026/08/secret.png"))
                .as("접두어를 안 보고 뒤쪽만 잘라 쓰면 임의 객체를 지우게 만들 수 있습니다")
                .isEmpty();
        assertThat(storage.objectKeyOf("https://other-bucket.s3." + REGION + ".amazonaws.com/a.png"))
                .as("다른 버킷도 우리 것이 아닙니다")
                .isEmpty();
        assertThat(storage.objectKeyOf("https://" + BUCKET + ".s3.us-east-1.amazonaws.com/a.png"))
                .as("리전이 다르면 다른 주소입니다")
                .isEmpty();
    }

    @Test
    @DisplayName("접두어만 있고 키가 없는 주소도 거른다")
    void bareBaseUrlYieldsNoKey() {
        assertThat(storage.objectKeyOf(BASE)).isEmpty();
        assertThat(storage.objectKeyOf(null)).isEmpty();
    }

    @Test
    @DisplayName("우리 주소면 접두어를 뗀 키가 나온다")
    void ourUrlYieldsTheKey() {
        assertThat(storage.objectKeyOf(BASE + "2026/08/abc.png")).contains("2026/08/abc.png");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 삭제
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 키·null 은 요청에 실리지 않는다")
    void blankKeysAreDropped() {
        storage.deleteAll(java.util.Arrays.asList("a/b.png", null, "  ", "a/b.png"));

        assertThat(s3.deleted)
                .as("중복도 한 번만 보냅니다")
                .containsExactly("a/b.png");
    }

    @Test
    @DisplayName("★ S3 가 실패해도 예외를 던지지 않는다 — 이미 커밋된 삭제를 되돌릴 수 없다")
    void deleteFailureDoesNotPropagate() {
        s3.failDeletes = true;

        // 여기서 던지면 공지는 이미 지워졌는데 오류만 나갑니다.
        storage.deleteAll(List.of("a/b.png"));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("files", name, "image/png", "x".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 우리가 S3 에 <b>무엇을 보내는지</b>만 받아 적습니다.
     *
     * <p>{@code S3Client} 의 메서드는 전부 기본 구현이 있어 필요한 둘만 덮어쓰면 됩니다.
     */
    private static final class RecordingS3Client implements S3Client {

        private final List<PutObjectRequest> puts = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private boolean failDeletes;

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            puts.add(request);
            return PutObjectResponse.builder().build();
        }

        @Override
        public DeleteObjectsResponse deleteObjects(DeleteObjectsRequest request) {
            if (failDeletes) {
                throw new IllegalStateException("S3 불통");
            }
            request.delete().objects().forEach(o -> deleted.add(o.key()));
            return DeleteObjectsResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {
        }
    }
}
