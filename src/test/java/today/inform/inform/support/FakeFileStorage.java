package today.inform.inform.support;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.multipart.MultipartFile;
import today.inform.inform.storage.AllowedImageType;
import today.inform.inform.storage.FileStorage;
import today.inform.inform.storage.StoredFile;

/**
 * S3 대역. <b>업로드 규칙과 삭제 규칙은 실제 S3 없이 검증할 수 있어야 합니다.</b>
 *
 * <p>진짜 S3 를 태우면 자격증명 없이는 빌드가 돌지 않고, LocalStack 을 붙이면
 * 모든 테스트가 컨테이너를 하나 더 기다립니다. 정작 검증하고 싶은 것
 * (확장자 화이트리스트 · 용량 · 연결된 파일 보호 · 삭제가 실제로 불리는가)은
 * 저장소 구현과 무관합니다.
 *
 * <p><b>★ 이 대역은 실제 구현을 검증하지 못합니다.</b> 키 형식·URL 조립·Content-Type 재판정·
 * 주소 접두어 가드가 {@code S3FileStorage} 에 있고 여기에 <b>같은 로직이 중복 구현</b>돼 있어서,
 * 실제 구현을 망가뜨려도 이 대역을 쓰는 테스트는 전부 통과합니다.
 * 그래서 그 넷은 {@link today.inform.inform.storage.S3FileStorageTest} 가 따로 검증합니다 —
 * 그 파일이 없으면 이 대역은 <b>검증이 아니라 검증처럼 보이는 것</b>일 뿐입니다.
 */
public class FakeFileStorage implements FileStorage {

    public static final String BASE_URL = "https://fake-bucket.s3.test.amazonaws.com/";

    private final Map<String, StoredFile> uploaded = new LinkedHashMap<>();
    private final Set<String> deletedKeys = new LinkedHashSet<>();

    @Override
    public StoredFile upload(MultipartFile file) {
        AllowedImageType type = AllowedImageType.fromFilename(file.getOriginalFilename()).orElse(null);
        String key = "2026/08/" + UUID.randomUUID() + (type == null ? "" : "." + type.extension());
        StoredFile stored = new StoredFile(
                BASE_URL + key,
                key,
                file.getOriginalFilename(),
                type == null ? "application/octet-stream" : type.contentType(),
                file.getSize());
        uploaded.put(key, stored);
        return stored;
    }

    @Override
    public void deleteAll(Collection<String> objectKeys) {
        deletedKeys.addAll(objectKeys);
        objectKeys.forEach(uploaded::remove);
    }

    @Override
    public Optional<String> objectKeyOf(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(BASE_URL)) {
            return Optional.empty();
        }
        String key = fileUrl.substring(BASE_URL.length());
        return key.isBlank() ? Optional.empty() : Optional.of(key);
    }

    /** 지우라고 요청받은 키 전부. "삭제가 실제로 불렸는가" 를 확인하는 데 씁니다. */
    public Set<String> deletedKeys() {
        return deletedKeys;
    }

    /** 아직 남아 있는 객체 수. */
    public int remaining() {
        return uploaded.size();
    }

    public void reset() {
        uploaded.clear();
        deletedKeys.clear();
    }

    /**
     * 실제 {@code S3FileStorage} 대신 이 대역을 주입합니다.
     *
     * <p>{@code @Primary} 라 같은 타입의 빈이 둘이어도 이쪽이 선택됩니다.
     */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public FakeFileStorage fakeFileStorage() {
            return new FakeFileStorage();
        }
    }
}
