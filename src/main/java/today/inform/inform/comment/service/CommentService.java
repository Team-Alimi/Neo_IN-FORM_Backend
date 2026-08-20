package today.inform.inform.comment.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

/**
 * CMT-01 ~ CMT-04.
 *
 * <p><b>깊이 제한과 상위 댓글 정합성은 여기서 검사하지 않습니다.</b>
 * DB 트리거가 IN004 / IN005 로 막고, {@code SqlStateErrorMapper} 가 400 으로 옮깁니다.
 * 앱에서 한 번 더 확인하면 "확인과 INSERT 사이" 라는 경합 구간이 생기고,
 * 규칙이 두 곳에 생겨 한쪽만 고치는 사고가 납니다. 판정은 한 곳에서만 합니다.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ArticleReadableChecker readableChecker;

    /**
     * CMT-02 목록. 원댓글은 시간순으로 페이징하고 답글은 그 아래 전부 붙입니다.
     *
     * <p>답글까지 페이징하지 않는 이유 — 1단계 제한이라 한 원댓글에 달리는 답글이 많아야 몇 개고,
     * 페이지 경계에서 답글이 잘리면 대화가 끊겨 읽을 수 없게 됩니다.
     *
     * <p>쿼리는 세 번(원댓글 개수·원댓글·답글) 나가고 댓글 수와 무관하게 고정입니다.
     */
    @Transactional(readOnly = true)
    public Page<CommentResponse> list(Long articleId, Long viewerId, Pageable pageable) {
        readableChecker.requireReadable(articleId);

        Page<CommentRow> roots = commentRepository.findRoots(articleId, pageable);
        if (roots.isEmpty()) {
            return roots.map(row -> CommentResponse.from(row, viewerId, List.of()));
        }

        Map<Long, List<CommentResponse>> repliesByParent =
                groupReplies(roots.getContent().stream().map(CommentRow::id).toList(), viewerId);

        return roots.map(row -> CommentResponse.from(
                row, viewerId, repliesByParent.getOrDefault(row.id(), List.of())));
    }

    /**
     * CMT-01 작성.
     *
     * <p>공지 가시성은 북마크·좋아요와 <b>같은 기준</b>을 씁니다.
     * 상세를 열 수 있으면 댓글도 달 수 있어야 합니다 — 북마크에서 재검수 중인 공지를 열었는데
     * 댓글창만 막혀 있으면 사용자는 이유를 알 수 없습니다.
     */
    @Transactional
    public CommentResponse create(Long articleId, Long userId, String content, Long parentId) {
        readableChecker.requireReadable(articleId);

        Comment saved = commentRepository.save(Comment.create(articleId, userId, parentId, content));

        // 아래 조회가 auto-flush 를 일으키므로 INSERT 가 여기서 실행됩니다.
        // 상위 댓글 규칙 위반(IN004/IN005)도 이 시점에 트리거가 잡아 400 으로 나갑니다.
        CommentRow row = commentRepository.findRowById(saved.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        return CommentResponse.from(row, userId, List.of());
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
     * <p><b>답글 유무에 따라 동작이 갈립니다.</b>
     * <ul>
     *   <li>답글 있음 — 자리를 남깁니다({@code deleted_at}). 지우면 그 아래 답글이
     *       {@code ON DELETE CASCADE} 로 함께 사라져 대화가 통째로 없어집니다.</li>
     *   <li>답글 없음 — 행을 지웁니다. 남길 이유가 없습니다.</li>
     * </ul>
     *
     * <p>판정과 삭제 <b>사이에 답글이 달리면 안 되므로</b> 행을 잠그고 시작합니다.
     * 잠그지 않으면 "답글 없음" 으로 판정한 직후 달린 답글이 함께 지워집니다.
     *
     * <p>{@code comment_count} 는 두 경우 모두 트리거가 알아서 내립니다 —
     * 앱이 세지 않습니다.
     */
    @Transactional
    public void delete(Long commentId, Long userId) {
        // 잠금이 먼저입니다. 읽고 나서 잠그면 그 사이에 답글이 들어옵니다.
        if (commentRepository.lockById(commentId).isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        Comment comment = commentRepository.findById(commentId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        requireAuthor(comment, userId);

        if (commentRepository.existsByParentId(commentId)) {
            comment.softDelete();
        } else {
            commentRepository.delete(comment);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Map<Long, List<CommentResponse>> groupReplies(List<Long> parentIds, Long viewerId) {
        Map<Long, List<CommentResponse>> byParent = new HashMap<>();
        for (CommentRow reply : commentRepository.findReplies(parentIds)) {
            byParent.computeIfAbsent(reply.parentId(), key -> new ArrayList<>())
                    .add(CommentResponse.from(reply, viewerId, List.of()));
        }
        return byParent;
    }

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
