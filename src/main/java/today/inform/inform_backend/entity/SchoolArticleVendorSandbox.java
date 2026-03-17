package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "school_article_vendor_sandbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SchoolArticleVendorSandbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sandbox_id", nullable = false)
    private SchoolArticleSandbox sandboxArticle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalUrl;
}
