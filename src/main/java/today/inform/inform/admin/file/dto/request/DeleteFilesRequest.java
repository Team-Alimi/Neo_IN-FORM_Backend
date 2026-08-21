package today.inform.inform.admin.file.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * FIL-02 업로드 취소.
 *
 * <p>공지에 <b>연결되기 전</b>에 취소한 파일만 지웁니다.
 * 이미 연결된 파일은 400 으로 거부하고 공지 수정·삭제 흐름으로 유도합니다 —
 * 여기서 지워 버리면 공지에는 깨진 이미지 링크만 남습니다.
 */
public record DeleteFilesRequest(
        @NotEmpty(message = "지울 파일을 선택해 주세요.")
        @Size(max = 100, message = "한 번에 100건까지 처리할 수 있습니다.")
        List<@NotNull(message = "파일 주소가 비어 있습니다.") String> fileUrls) {
}
