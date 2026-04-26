package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {
    private String fileUrl;
    private String fileName;
    private long fileSize;
}
