package today.inform.inform_backend.dto;

import lombok.Builder;
import lombok.Getter;
import today.inform.inform_backend.entity.VendorType;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {
    private Integer notificationId;
    private String title;
    private String message;
    private VendorType articleType;
    private Integer articleId;
    private boolean isRead;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;
}
