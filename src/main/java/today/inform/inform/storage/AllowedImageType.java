package today.inform.inform.storage;

import java.util.Locale;
import java.util.Optional;

/**
 * 업로드를 허용하는 이미지 형식. 명세 4.9 의 목록입니다.
 *
 * <p><b>Content-Type 을 여기서 정하는 것이 핵심입니다.</b>
 * 클라이언트가 보낸 {@code Content-Type} 을 그대로 S3 에 저장하면,
 * {@code evil.png} 라는 이름에 {@code text/html} 을 실어 올린 파일을
 * <b>버킷 도메인이 HTML 로 서빙합니다.</b> 저장형 XSS 가 되고, 그 도메인은 우리 것입니다.
 * 확장자를 화이트리스트로 거른 뒤 <b>그 확장자에 대응하는 타입</b>만 붙입니다.
 *
 * <p>물론 이것이 내용까지 보장하지는 않습니다 — 실제로 PNG 인지는 확인하지 않습니다.
 * 다만 브라우저가 그것을 문서로 해석하는 경로는 막힙니다.
 */
public enum AllowedImageType {

    JPG("jpg", "image/jpeg"),
    JPEG("jpeg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    AllowedImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /** 허용 목록을 사용자에게 보여 줄 때. */
    public static String allowedExtensions() {
        return String.join(", ", java.util.Arrays.stream(values()).map(AllowedImageType::extension).toList());
    }

    /**
     * 파일 이름의 확장자로 형식을 고릅니다.
     *
     * <p>{@code Locale.ROOT} 로 낮춥니다 — 터키어 로케일에서 {@code I → ı} 가 되어
     * {@code .GIF} 가 목록에 없는 것으로 판정되는 것을 피하기 위함입니다.
     */
    public static Optional<AllowedImageType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(values())
                .filter(type -> type.extension.equals(extension))
                .findFirst();
    }
}
