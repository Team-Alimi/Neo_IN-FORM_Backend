package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "school_articles",
    indexes = {
        @Index(name = "idx_school_article_category", columnList = "category_id"),
        @Index(name = "idx_school_article_dates", columnList = "startDate, dueDate"),
        @Index(name = "idx_article_published", columnList = "isPublished"),
        @Index(name = "idx_article_admin_status", columnList = "isPublished, adminStatus, created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SchoolArticle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer articleId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private LocalDate startDate;
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    @Builder.Default
    private boolean isPublished = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AdminStatus adminStatus = AdminStatus.INSPECTED_YET;

    @Enumerated(EnumType.STRING)
    private AdminStatus previousStatus;

    public void update(String title, String content, LocalDate startDate, LocalDate dueDate, Category category) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.category = category;
    }

    public void updateStatus(AdminStatus adminStatus) {
        this.adminStatus = adminStatus;
    }

    public void moveToGarbage() {
        this.previousStatus = this.adminStatus;
        this.adminStatus = AdminStatus.GARBAGE;
    }

    public void restore() {
        if (this.previousStatus != null) {
            this.adminStatus = this.previousStatus;
        } else {
            this.adminStatus = AdminStatus.INSPECTED_YET;
        }
        this.previousStatus = null;
    }

    public void deploy() {
        this.isPublished = true;
    }
}
