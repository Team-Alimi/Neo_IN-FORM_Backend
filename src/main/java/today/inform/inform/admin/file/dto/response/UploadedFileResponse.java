package today.inform.inform.admin.file.dto.response;

import today.inform.inform.storage.StoredFile;

/**
 * FIL-01 업로드 결과 한 건.
 *
 * <p><b>{@code object_key} 를 내보내지 않습니다.</b> 화면은 {@code file_url} 만 있으면 되고,
 * 키를 알려 주면 클라이언트가 그것으로 삭제를 시도하게 됩니다.
 * 키는 서버가 URL 에서 되짚습니다({@code FileStorage#objectKeyOf}) —
 * 그래야 우리 버킷 밖 주소가 삭제 요청에 섞여 들어와도 걸러집니다.
 */
public record UploadedFileResponse(
        String fileUrl,
        String originalName,
        String contentType,
        long sizeBytes) {

    public static UploadedFileResponse from(StoredFile stored) {
        return new UploadedFileResponse(
                stored.fileUrl(), stored.originalName(), stored.contentType(), stored.sizeBytes());
    }
}
