package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attachment_sandbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AttachmentSandbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String attachmentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sandbox_id", nullable = false)
    private SchoolArticleSandbox sandboxArticle;
}
