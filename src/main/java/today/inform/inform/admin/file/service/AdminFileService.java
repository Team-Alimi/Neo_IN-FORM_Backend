package today.inform.inform.admin.file.service;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import today.inform.inform.admin.file.dto.response.UploadedFileResponse;
import today.inform.inform.admin.file.repository.AttachmentQueryRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.storage.AllowedImageType;
import today.inform.inform.storage.FileStorage;

/**
 * FIL-01 업로드 · FIL-02 취소.
 *
 * <p><b>업로드 시점에는 DB 레코드가 생기지 않습니다</b>(명세 4.9).
 * 파일은 S3 에만 올라가고, 공지를 저장할 때 {@code file_url} 을 넘기면 그때 연결됩니다.
 * 관리자가 이미지를 올려 두고 작성을 취소하면 <b>연결되지 않은 객체가 남습니다</b> —
 * 그걸 정리하는 것이 FIL-02 이고, 사용자가 취소조차 안 한 경우는 정리 배치가 필요합니다.
 *
 * <p><b>v1 이 여기서 실패했습니다.</b> 삭제 코드는 있었지만 <b>어디서도 불리지 않아</b>
 * 공지를 지워도 스토리지 객체가 그대로 남았습니다. 그래서 이번에는
 * 영구 삭제(ADM-10)가 S3 정리를 함께 부르고, 그 경로에 테스트를 붙여 둡니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileService {

    /** 명세 4.9. 공지 본문에 들어가는 이미지라 이 이상은 화면에서도 의미가 없습니다. */
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final FileStorage fileStorage;
    private final AttachmentQueryRepository attachmentQueryRepository;

    /**
     * FIL-01. 여러 건을 한 번에 올립니다.
     *
     * <p><b>검증을 전부 먼저 합니다.</b> 올리면서 검사하면 세 번째 파일이 규격 위반일 때
     * 앞의 두 개는 이미 S3 에 올라간 채로 400 이 나가고, 그 둘은 아무도 모르는 고아가 됩니다.
     */
    public List<UploadedFileResponse> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY, "업로드할 파일이 없습니다.");
        }
        files.forEach(AdminFileService::validate);

        return files.stream()
                .map(fileStorage::upload)
                .map(UploadedFileResponse::from)
                .toList();
    }

    /**
     * FIL-02. <b>공지에 연결되지 않은 파일만</b> 지웁니다.
     *
     * <p>연결된 파일이 하나라도 섞여 있으면 <b>아무것도 지우지 않고</b> 거부합니다.
     * 일부만 지우면 관리자는 200 을 받고 나머지가 남은 줄 모릅니다.
     *
     * <p>{@code @Transactional} 이 아닙니다 — S3 삭제는 되돌릴 수 없어서
     * 트랜잭션 안에 넣어 봐야 롤백돼도 객체는 이미 사라진 뒤입니다.
     * DB 조회는 읽기뿐이므로 트랜잭션이 필요하지도 않습니다.
     *
     * @return 실제로 지운 개수
     */
    public int deleteUnlinked(List<String> fileUrls) {
        List<String> urls = fileUrls.stream().filter(Objects::nonNull).distinct().toList();

        List<String> linked = attachmentQueryRepository.findLinkedUrls(urls);
        if (!linked.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "이미 공지에 연결된 파일은 여기서 지울 수 없습니다. 공지를 수정하거나 삭제해 주세요. "
                            + "대상=" + linked);
        }

        // 우리 버킷 주소가 아닌 것은 조용히 버립니다 —
        // 남의 주소를 넣어 우리 버킷의 다른 객체를 지우게 만들 수 없어야 합니다.
        List<String> keys = urls.stream()
                .map(fileStorage::objectKeyOf)
                .flatMap(java.util.Optional::stream)
                .toList();

        if (keys.size() != urls.size()) {
            log.warn("우리 스토리지의 주소가 아닌 파일이 삭제 요청에 섞여 있어 건너뜁니다. 요청={} 처리={}",
                    urls.size(), keys.size());
        }
        fileStorage.deleteAll(keys);
        return keys.size();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        // ★ 판정 기준은 확장자입니다. 클라이언트가 보낸 Content-Type 은 믿지 않습니다 —
        //   그건 요청자가 마음대로 정할 수 있어서 검사로서 의미가 없습니다.
        //   저장할 때 붙는 타입도 이 확장자에서 다시 뽑습니다(AllowedImageType 주석 참조).
        if (AllowedImageType.fromFilename(file.getOriginalFilename()).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_FILE_TYPE,
                    "허용되지 않는 파일 형식입니다. (허용: " + AllowedImageType.allowedExtensions() + ")");
        }
    }
}
