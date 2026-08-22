package today.inform.inform.comment.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.service.ArticleReadableChecker;
import today.inform.inform.comment.dto.response.CommentResponse;
import today.inform.inform.comment.entity.Comment;
import today.inform.inform.comment.repository.CommentRepository;
import today.inform.inform.comment.repository.CommentRow;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.notification.service.NotificationService;

/**
 * CMT-01 ~ CMT-04.
 *
 * <p><b>답글이 없습니다.</b> 댓글은 공지 하나에 달리는 평면 목록이고 대댓글을 달 수 없습니다.
 * 그래서 여기에는 깊이 판정도, 자리를 남기는 삭제도, 답글 알림도 없습니다.
 * ({@code comments.parent_id} 컬럼과 관련 트리거는 스키마에 남아 있지만 앱이 채우지 않습니다)
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ArticleReadableChecker readableChecker;
    private final NotificationService notificationService;

    /**
     * CMT-02 목록. 시간순 평면 목록입니다.
     *
     * <p>쿼리는 2~3번 나갑니다 — 가시성 확인 1, 목록 1, 전체 개수 1(페이지가 가득 찼을 때만).
     * <b>댓글 수에 비례해 늘어나지 않는 것</b>이 요점입니다.
     */
    @Transactional(readOnly = true)
    public Page<CommentResponse> list(Long articleId, Long viewerId, Pageable pageable) {
        readableChecker.requireReadable(articleId);

        return commentRepository.findByArticle(articleId, dropSort(pageable))
                .map(row -> CommentResponse.from(row, viewerId));
    }

    /**
     * 클라이언트가 보낸 정렬을 버립니다.
     *
     * <p>댓글 정렬은 시간순으로 고정입니다. 그런데 Spring Data 는 {@code @Query} 의
     * {@code ORDER BY} <b>뒤에 요청한 정렬을 문자열로 이어 붙입니다.</b> 검증은 하지 않습니다.
     * {@code ?sort=foo} 하나면 JPQL 이 {@code ... , c.foo asc} 가 되어 파싱 단계에서 터지고,
     * SQLSTATE 가 없는 예외라 <b>500</b> 으로 나갑니다.
     * 반대로 {@code ?sort=createdAt,desc} 는 200 이지만 고정 정렬 뒤에 붙어 아무 효과가 없습니다 —
     * 클라이언트는 내림차순을 요청하고 오름차순을 받습니다.
     *
     * <p>공지 목록은 화이트리스트({@code ArticleSortSanitizer})가 있어 안전하지만
     * 여기는 정렬이 고정이라 받을 이유 자체가 없습니다.
     */
    private static Pageable dropSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * CMT-01 작성.
     *
     * <p>공지 가시성은 북마크·좋아요와 <b>같은 기준</b>을 씁니다.
     * 상세를 열 수 있으면 댓글도 달 수 있어야 합니다 — 북마크에서 재검수 중인 공지를 열었는데
     * 댓글창만 막혀 있으면 사용자는 이유를 알 수 없습니다.
     */
    @Transactional
    public CommentResponse create(Long articleId, Long userId, String content) {
        readableChecker.requireReadable(articleId);

        Comment saved = commentRepository.save(Comment.create(articleId, userId, content));

        // 작성자 이름이 붙은 응답을 만들기 위한 조회입니다. 엔티티에는 user_id 만 있어서
        // 그대로 응답을 만들면 방금 쓴 댓글만 작성자 없이 그려집니다.
        CommentRow row = commentRepository.findRowById(saved.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        return CommentResponse.from(row, userId);
    }

    /** CMT-03 수정. 본인만. */
    @Transactional
    public void update(Long commentId, Long userId, String content) {
        Comment comment = findEditable(commentId, userId);
        comment.edit(content);
    }

    /**
     * CMT-04 삭제. 본인만.
     *
     * <p><b>행을 지웁니다.</b> 예전에는 답글이 달린 댓글의 자리를 {@code deleted_at} 으로 남겼지만,
     * 답글이 없어지면서 남길 이유가 사라졌습니다 — 아래에 매달린 것이 없으므로
     * 지워도 남의 글이 함께 사라지지 않습니다.
     *
     * <p>{@code comment_count} 는 트리거가 알아서 내립니다. 앱이 세지 않습니다.
     */
    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        requireAuthor(comment, userId);

        commentRepository.delete(comment);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Comment findEditable(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                // 삭제된 댓글은 없는 것으로 다룹니다. 자리만 남은 껍데기를 고치게 둘 이유가 없습니다.
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        requireAuthor(comment, userId);
        return comment;
    }

    /**
     * 남의 댓글이면 403 입니다. 여기서는 404 로 감출 이유가 없습니다 —
     * 댓글은 목록에 이미 공개돼 있어서 존재 자체가 비밀이 아닙니다.
     */
    private void requireAuthor(Comment comment, Long userId) {
        if (!comment.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 댓글만 수정·삭제할 수 있습니다.");
        }
    }
}
