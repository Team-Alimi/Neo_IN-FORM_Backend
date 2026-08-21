package today.inform.inform.storage;

import java.util.Collection;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부 파일 저장소.
 *
 * <p><b>인터페이스로 둔 이유는 테스트입니다.</b> 업로드 규칙(확장자·크기·키 형식)과
 * 삭제 규칙(연결된 파일은 못 지움)은 S3 없이도 전부 검증할 수 있어야 합니다.
 * 실제 S3 왕복까지 통합 테스트에 넣으면 자격증명 없이는 빌드가 돌지 않습니다.
 */
public interface FileStorage {

    /**
     * 파일 하나를 올립니다.
     *
     * @throws today.inform.inform.global.exception.BusinessException 업로드 실패 시 FILE_UPLOAD_FAILED
     */
    StoredFile upload(MultipartFile file);

    /**
     * 객체를 지웁니다. <b>없는 키를 지우는 것은 오류가 아닙니다</b> —
     * 재시도나 중복 요청에서 흔하고, 결과 상태는 어느 쪽이든 "없음" 으로 같습니다.
     *
     * <p>일부만 실패해도 나머지는 계속 지웁니다. 실패한 키는 로그로 남깁니다 —
     * 여기서 예외를 던지면 <b>DB 는 이미 지워졌는데 트랜잭션만 되살아나는</b> 어긋난 상태가 됩니다.
     */
    void deleteAll(Collection<String> objectKeys);

    /**
     * 공개 URL 에서 내부 키를 되짚습니다.
     *
     * <p>업로드 응답에는 URL 만 실려 나가므로, 관리자가 취소한 파일을 지울 때(FIL-02)
     * 이 변환이 필요합니다. <b>우리가 만든 URL 이 아니면 비어 있습니다</b> —
     * 남의 주소를 넣어 우리 버킷의 다른 객체를 지우게 만들 수 없어야 합니다.
     */
    java.util.Optional<String> objectKeyOf(String fileUrl);
}
