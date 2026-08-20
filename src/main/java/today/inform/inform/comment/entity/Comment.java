package today.inform.inform.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import today.inform.inform.global.entity.BaseTimeEntity;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 공지 댓글. <b>1단계 답글까지만</b> 허용합니다.
 *
 * <p>깊이 제한과 상위 댓글 정합성은 <b>DB 트리거가 강제</b>합니다
 * ({@code trg_comments_10_parent_integrity}). 앱의 검증은 사용자 경험용 1차 방어일 뿐이고,
 * 최종 판정은 DB 가 합니다. 앱만 검사하면 크롤러·관리도구 등 다른 경로가 규칙을 우회합니다.
 *
 * <p><b>연관관계를 쓰지 않는 이유</b>
 * {@code article}·{@code user}·{@code parent} 를 전부 {@code @ManyToOne} 으로 두면
 * 목록 한 페이지에 프록시가 수십 개 생깁니다. 작성자 이름은 어차피 DTO projection 으로
 * 한 번에 join 해 가져오므로 연관관계로 얻을 게 없습니다.
 *
 * <p><b>삭제 정책</b>
 * 답글이 달린 댓글을 지우면 스레드가 무너지므로 자리를 남깁니다({@code deleted_at}).
 * 답글이 없으면 흔적을 남길 이유가 없어 행을 지웁니다. 판정은 서비스가 합니다.
 */
@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    /** 순수 텍스트 기준. HTML 을 허용하지 않으므로 태그 길이를 따질 필요가 없습니다. */
    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, updatable = false)
    private Long articleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** null 이면 원댓글. 값이 있으면 답글이고, 그 대상은 반드시 원댓글이어야 합니다(IN004). */
    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** soft delete. 값이 있으면 화면에 자리만 남고 {@code comment_count} 에서도 빠집니다. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private Comment(Long articleId, Long userId, Long parentId, String content) {
        validateContent(content);
        this.articleId = articleId;
        this.userId = userId;
        this.parentId = parentId;
        this.content = content;
    }

    public static Comment create(Long articleId, Long userId, Long parentId, String content) {
        return new Comment(articleId, userId, parentId, content);
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isAuthor(Long userId) {
        return this.userId.equals(userId);
    }

    /** CMT-03. 작성자 확인은 서비스가 먼저 합니다. */
    public void edit(String newContent) {
        validateContent(newContent);
        this.content = newContent;
    }

    /**
     * 답글이 달려 있어 자리를 남겨야 하는 경우.
     *
     * <p>본문은 지웁니다. {@code deleted_at} 만 세우고 내용을 남기면
     * DB 를 직접 보는 사람에게는 그대로 읽히고, 나중에 목록 쿼리 한 줄만 잘못 고쳐도
     * <b>삭제한 내용이 화면에 다시 나타납니다.</b>
     */
    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
        this.content = "";
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "댓글 내용을 입력해 주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "댓글은 " + MAX_CONTENT_LENGTH + "자를 넘을 수 없습니다.");
        }
    }
}
