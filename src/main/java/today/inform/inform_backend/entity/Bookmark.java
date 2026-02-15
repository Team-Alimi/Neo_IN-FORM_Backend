package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "bookmarks",
    indexes = {
        @Index(name = "idx_bookmark_user", columnList = "user_id"),
        @Index(name = "idx_bookmark_article", columnList = "articleType, articleId")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Bookmark extends BaseCreatedTimeEntity { // createdAt만 상속

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer bookmarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorType articleType;

    @Column(nullable = false)
    private Integer articleId;
}