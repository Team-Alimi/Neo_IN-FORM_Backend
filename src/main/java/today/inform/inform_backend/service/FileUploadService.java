package today.inform.inform_backend.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.FileUploadResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final Storage storage;

    @Value("${gcs.bucket-name}")
    private String bucketName;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 단일 파일 업로드
     */
    public FileUploadResponse uploadFile(MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String storagePath = generateStoragePath(extension);

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, storagePath)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getBytes());
        } catch (IOException e) {
            log.error("파일 업로드 실패: {}", originalFilename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String publicUrl = String.format(
                "https://storage.googleapis.com/%s/%s", bucketName, storagePath
        );

        return FileUploadResponse.builder()
                .fileUrl(publicUrl)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .build();
    }

    /**
     * 다중 파일 업로드
     */
    public List<FileUploadResponse> uploadFiles(List<MultipartFile> files) {
        return files.stream()
                .map(this::uploadFile)
                .collect(Collectors.toList());
    }

    /**
     * GCS 파일 삭제 (게시글 삭제 시 활용 가능)
     */
    public void deleteFile(String fileUrl) {
        String prefix = String.format("https://storage.googleapis.com/%s/", bucketName);
        if (!fileUrl.startsWith(prefix)) {
            return; // GCS URL이 아니면 무시 (기존 외부 링크 호환)
        }

        String objectName = fileUrl.substring(prefix.length());
        try {
            storage.delete(bucketName, objectName);
        } catch (Exception e) {
            log.warn("GCS 파일 삭제 실패: {}", fileUrl, e);
        }
    }

    // ==================== 내부 유틸 메서드 ==================== //

    private void validateFile(MultipartFile file) {
        // 빈 파일 검증
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 확장자 검증
        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        // Content-Type 검증
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String generateStoragePath(String extension) {
        LocalDate now = LocalDate.now();
        String uuid = UUID.randomUUID().toString();
        return String.format("attachments/%d/%02d/%s.%s",
                now.getYear(), now.getMonthValue(), uuid, extension);
    }
}
