package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.FileUploadResponse;
import today.inform.inform_backend.service.FileUploadService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 이미지 파일 업로드 (단일/다중 통합)
     */
    @PostMapping("/files/upload")
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadFiles(
            @RequestPart("files") List<MultipartFile> files) {
        List<FileUploadResponse> responses = fileUploadService.uploadFiles(files);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
