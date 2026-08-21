package today.inform.inform.storage;

/**
 * 스토리지에 올라간 파일 하나.
 *
 * @param fileUrl      공개 접근 주소. 공지 저장 시 {@code attachments.file_url} 로 넘어갑니다
 * @param objectKey    스토리지 내부 키. <b>지울 때 필요합니다.</b>
 *                     URL 에서 매번 되짚으면 버킷·도메인이 바뀌는 순간 삭제가 조용히 실패하므로
 *                     {@code attachments.object_key} 에 함께 저장합니다
 *                     (DB CHECK 가 {@code (storage_type='S3') = (object_key IS NOT NULL)} 를 강제합니다)
 * @param originalName 사용자가 올린 이름. 화면 표시용이며 키에는 쓰지 않습니다
 * @param sizeBytes    0 보다 커야 합니다 — DB CHECK 가 강제합니다
 */
public record StoredFile(
        String fileUrl,
        String objectKey,
        String originalName,
        String contentType,
        long sizeBytes) {
}
