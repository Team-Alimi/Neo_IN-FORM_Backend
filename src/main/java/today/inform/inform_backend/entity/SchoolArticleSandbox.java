package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "school_article_sandbox",
    indexes = {
        @Index(name = "idx_sandbox_status_created", columnList = "admin_status, created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SchoolArticleSandbox extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer sandboxId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private LocalDate startDate;
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AdminStatus adminStatus = AdminStatus.INSPECTED_YET;
}
