package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SandboxArticleResponse {
    private Integer sandboxId;
    private String title;
    private String content;
    private String categoryName;
    private String adminStatus;
    private LocalDate startDate;
    private LocalDate dueDate;
    
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;
    
    private List<String> vendorNames;
    private List<String> originalUrls;
    private List<String> attachmentUrls;
}
