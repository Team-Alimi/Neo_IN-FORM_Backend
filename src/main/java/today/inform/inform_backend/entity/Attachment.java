package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attachment { // 타임스탬프 없음

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String attachmentUrl;

    @Column(nullable = false)
    private Integer articleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorType articleType;
}