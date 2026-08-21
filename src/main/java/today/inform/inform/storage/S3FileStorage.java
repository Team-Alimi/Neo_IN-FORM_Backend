package today.inform.inform.storage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * S3 구현.
 *
 * <p><b>클라이언트를 처음 쓸 때 만듭니다.</b> 빈 생성 시점에 만들면 자격증명이 없는 환경
 * (통합 테스트·로컬 개발)에서 <b>애플리케이션 컨텍스트 자체가 뜨지 않습니다.</b>
 * 파일 업로드는 관리자만 쓰는 기능인데 그것 때문에 전체가 기동하지 못하면 안 됩니다.
 *
 * <p><b>키에 원본 파일명을 쓰지 않습니다.</b> 사용자가 올리는 이름에는 한글·공백·{@code ../}
 * 가 섞이고, 같은 이름이 덮어쓰기를 일으킵니다. UUID 로 만들고 원본 이름은
 * {@code attachments.original_name} 에 따로 둡니다.
 */
@Slf4j
@Component
public class S3FileStorage implements FileStorage {

    private static final DateTimeFormatter KEY_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 한 번에 지울 수 있는 객체 수. S3 DeleteObjects 의 상한입니다. */
    private static final int DELETE_BATCH_SIZE = 1000;

    private final String bucket;
    private final String region;
    private final Supplier<S3Client> client;

    @org.springframework.beans.factory.annotation.Autowired
    public S3FileStorage(@Value("${aws.s3.bucket}") String bucket,
                         @Value("${aws.region}") String region) {
        this(bucket, region, memoize(() -> S3Client.builder().region(Region.of(region)).build()));
    }

    /**
     * 테스트용. 클라이언트를 밖에서 넣습니다.
     *
     * <p>생성자가 둘이 되므로 위쪽에 {@code @Autowired} 가 필요합니다 —
     * 없으면 Spring 이 어느 것을 쓸지 정하지 못해 <b>컨텍스트 자체가 뜨지 않습니다.</b>
     *
     * <p>이게 없으면 이 클래스의 <b>어떤 것도 검증할 수 없습니다</b> —
     * 업로드 경로가 실제 S3 를 타므로 자격증명 없이는 못 돌리고, 그래서 대역만 검증하게 됩니다.
     * 그런데 키 형식·URL 조립·Content-Type 재판정·주소 접두어 가드가 전부 여기 있습니다.
     * (실제로 대역만 보는 동안 그 넷이 통째로 미검증이었습니다)
     */
    S3FileStorage(String bucket, String region, Supplier<S3Client> client) {
        this.bucket = bucket;
        this.region = region;
        this.client = client;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Content-Type 은 클라이언트가 보낸 값을 쓰지 않습니다.</b>
     * 확장자로 판정한 타입을 붙입니다 — 이유는 {@link AllowedImageType} 주석에 있습니다.
     * 여기 도달했다는 것은 {@code AdminFileService} 가 확장자를 이미 화이트리스트로 걸렀다는 뜻이므로
     * 판정이 비는 경우는 정상 경로에 없지만, 비면 브라우저가 해석하지 않는 타입으로 떨어뜨립니다.
     */
    @Override
    public StoredFile upload(MultipartFile file) {
        AllowedImageType type = AllowedImageType.fromFilename(file.getOriginalFilename()).orElse(null);
        String contentType = (type == null) ? "application/octet-stream" : type.contentType();
        String key = newObjectKey(type);

        try {
            client.get().putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | RuntimeException e) {
            log.error("S3 업로드 실패. key={} size={}", key, file.getSize(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return new StoredFile(
                urlOf(key), key, file.getOriginalFilename(), contentType, file.getSize());
    }

    /**
     * {@inheritDoc}
     *
     * <p>실패해도 예외를 던지지 않습니다. 이 메서드는 DB 커밋이 끝난 뒤에 불리므로
     * 여기서 던지면 <b>이미 지워진 공지를 되살릴 수 없는 채로</b> 오류만 나갑니다.
     * 남은 객체는 고아가 되지만, 그건 정리 배치가 다룰 문제입니다.
     */
    @Override
    public void deleteAll(Collection<String> objectKeys) {
        java.util.List<String> keys = objectKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();

        for (int from = 0; from < keys.size(); from += DELETE_BATCH_SIZE) {
            java.util.List<String> batch = keys.subList(from, Math.min(from + DELETE_BATCH_SIZE, keys.size()));
            try {
                client.get().deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder()
                                .objects(batch.stream()
                                        .map(key -> ObjectIdentifier.builder().key(key).build())
                                        .toList())
                                .build())
                        .build());
            } catch (RuntimeException e) {
                // ★ 조용히 넘기지 않습니다. 지워지지 않은 객체는 비용이자 유출면입니다.
                log.error("S3 객체 삭제 실패. 고아 객체가 남았습니다. keys={}", batch, e);
            }
        }
    }

    @Override
    public Optional<String> objectKeyOf(String fileUrl) {
        if (fileUrl == null) {
            return Optional.empty();
        }
        String prefix = baseUrl();
        // ★ "우리 버킷 주소로 시작하는가" 를 확인합니다. 이걸 빼고 URL 끝부분만 잘라 쓰면
        //   남의 주소를 넣어 우리 버킷의 임의 객체를 지우게 만들 수 있습니다.
        if (!fileUrl.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = fileUrl.substring(prefix.length());
        return key.isBlank() ? Optional.empty() : Optional.of(key);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code 2026/08/<uuid>.png} — 월별로 나눠 한 접두어에 객체가 무한히 쌓이지 않게 합니다.
     *
     * <p><b>원본 파일명은 키에 넣지 않습니다.</b> 한글·공백·{@code ../} 가 섞이고,
     * 같은 이름이 서로를 덮어씁니다. 확장자만 <b>화이트리스트에서 고른 값</b>으로 붙입니다 —
     * 사용자 문자열을 키에 이어 붙이는 경로를 아예 만들지 않습니다.
     */
    private static String newObjectKey(AllowedImageType type) {
        String extension = (type == null) ? "" : "." + type.extension();
        return LocalDate.now().format(KEY_PREFIX) + "/" + UUID.randomUUID() + extension;
    }

    private String urlOf(String key) {
        return baseUrl() + key;
    }

    private String baseUrl() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }

    /** 처음 호출될 때 한 번만 만들고 이후로는 같은 것을 돌려줍니다. */
    private static <T> Supplier<T> memoize(Supplier<T> delegate) {
        return new Supplier<>() {
            private volatile T value;

            @Override
            public T get() {
                T local = value;
                if (local == null) {
                    synchronized (this) {
                        local = value;
                        if (local == null) {
                            value = local = delegate.get();
                        }
                    }
                }
                return local;
            }
        };
    }
}
