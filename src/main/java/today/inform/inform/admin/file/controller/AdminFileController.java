package today.inform.inform.admin.file.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import today.inform.inform.admin.file.dto.request.DeleteFilesRequest;
import today.inform.inform.admin.file.dto.response.UploadedFileResponse;
import today.inform.inform.admin.file.service.AdminFileService;
import today.inform.inform.global.response.ApiResponse;

/**
 * FIL-01 업로드 · FIL-02 취소. {@code /admin/**} 전체가 {@code hasRole("ADMIN")} 입니다.
 *
 * <p>업로드는 <b>S3 에만</b> 올립니다. 공지와의 연결은 저장 시점에
 * {@code PATCH·POST /admin/articles} 의 {@code attachments} 로 넘겨야 생깁니다.
 */
@RestController
@RequestMapping("/admin/files")
@RequiredArgsConstructor
public class AdminFileController {

    private final AdminFileService adminFileService;

    /**
     * FIL-01. {@code multipart/form-data}, part 이름은 {@code files}(단일·다중 공통).
     *
     * <p>제한은 jpg·jpeg·png·gif·webp / 10MB 이고, <b>한 건이라도 어긋나면 전부 거부</b>합니다.
     */
    @PostMapping
    public ApiResponse<List<UploadedFileResponse>> upload(
            @RequestPart(name = "files") List<MultipartFile> files) {
        return ApiResponse.success(adminFileService.upload(files));
    }

    /**
     * FIL-02. 공지에 연결되기 <b>전에</b> 취소한 파일을 정리합니다.
     *
     * <p>이미 연결된 파일이 섞여 있으면 400 이고 아무것도 지우지 않습니다.
     */
    @DeleteMapping
    public ApiResponse<Map<String, Integer>> delete(@Valid @RequestBody DeleteFilesRequest request) {
        return ApiResponse.success(
                Map.of("deleted", adminFileService.deleteUnlinked(request.fileUrls())));
    }
}
