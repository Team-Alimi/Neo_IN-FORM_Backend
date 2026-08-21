package today.inform.inform.admin.comment.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.comment.dto.request.AdminCommentSearchCondition;
import today.inform.inform.admin.comment.dto.response.AdminCommentSummary;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.comment.repository.AdminCommentQueryRepository;
import today.inform.inform.comment.entity.Comment;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.comment.repository.CommentRepository;

/**
 * ADM-17 댓글 관리.
 *
 * <p>운영 원칙("관리자 페이지에서 완결")에 따라 DB 를 직접 열지 않고 부적절한 댓글을 지웁니다.
 *
 * <p><b>삭제 규칙은 사용자 삭제(CMT-04)와 똑같습니다.</b> 작성자 확인만 건너뜁니다.
 * 관리자에게 별도 규칙을 주면 —— 예를 들어 "관리자는 답글까지 통째로 지운다" ——
 * 같은 화면에 남아 있는 답글들이 <b>글쓴이가 지운 것과 구별되지 않게</b> 사라집니다.
 * 답글을 쓴 사람은 자기 글이 왜 없어졌는지 알 방법이 없습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentService {

    private final AdminCommentQueryRepository queryRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public Page<AdminCommentSummary> search(AdminCommentSearchCondition condition, Pageable pageable) {
        return queryRepository.search(condition, pageable);
    }

    /**
     * 일괄 삭제.
     *
     * <p><b>이미 없거나 이미 지워진 댓글은 건너뜁니다.</b> 404 로 전체를 뒤집지 않습니다 —
     * 목록을 띄워 둔 사이에 글쓴이가 먼저 지우는 일은 흔한데, 그때마다 나머지 199 건이
     * 함께 실패하면 관리자는 화면을 새로 고쳐 다시 고르는 일을 반복하게 됩니다.
     *
     * <p><b>남는 기록은 애플리케이션 로그뿐입니다.</b> 공지 상태 변경과 달리 댓글에는
     * 감사 테이블이 없고, 하드 삭제된 행은 되살릴 수 없습니다.
     * 관리자 조작 전용 감사 테이블이 생기면 그쪽으로 옮겨야 합니다
     * (같은 공백이 {@code ArticleMergeService#deletePermanently} 에도 있습니다).
     *
     * <p><b>응답 형태는 공지 벌크와 같습니다</b>(명세 4.8 공통 규약: {@code succeeded}/{@code failed}).
     * 경로가 {@code /bulk/} 인데 형태만 다르면 화면이 두 가지 응답을 따로 다뤄야 합니다.
     *
     * @return 건별 성공·실패. 이미 지워진 건은 {@code failed} 에 사유와 함께 담깁니다 —
     *         조용히 빼면 관리자는 30건을 골랐는데 28건만 처리된 것을 알 수 없습니다
     */
    @Transactional
    public BulkResult deleteAll(List<Long> commentIds, Long actorId) {
        List<Long> ids = commentIds.stream().filter(Objects::nonNull).distinct().toList();

        // 답글 → 원댓글 순. 답글을 먼저 없애야 원댓글이 빈 껍데기로 남지 않습니다.
        List<Long> ordered = queryRepository.findDeletionOrder(ids);

        // ★ 지우기 전에 대상 전부를 먼저 잠급니다. 이유는 lockAll 주석 참조.
        lockAll(ordered);

        BulkResult.Builder result = BulkResult.builder();

        // 조회 단계에서 이미 빠진 id (그 사이 글쓴이가 먼저 지운 경우)
        Set<Long> found = Set.copyOf(ordered);
        for (Long commentId : ids) {
            if (!found.contains(commentId)) {
                result.fail(commentId, ErrorCode.COMMENT_NOT_FOUND, "이미 삭제된 댓글입니다.");
            }
        }
        for (Long commentId : ordered) {
            if (deleteOne(commentId, actorId)) {
                result.succeed(commentId);
            } else {
                result.fail(commentId, ErrorCode.COMMENT_NOT_FOUND, "이미 삭제된 댓글입니다.");
            }
        }
        return result.build();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 대상 전부를 <b>지우기 전에</b> 정해진 순서로 잠급니다.
     *
     * <p><b>왜 삭제와 섞지 않고 따로 한 바퀴를 도는가 — 교착 때문입니다.</b>
     * 댓글을 하나 지울 때마다 {@code trg_comments_90_count} 가
     * {@code UPDATE articles SET comment_count = ...} 를 돌려 <b>articles 행에 배타 잠금</b>을 잡습니다.
     * 잠금과 삭제를 번갈아 하면 순서가 이렇게 됩니다 —
     * comments(첫 건) → articles → comments(둘째 건).
     *
     * <p>그런데 같은 공지에 답글을 다는 일반 사용자는 <b>반대 순서</b>로 잡습니다:
     * INSERT 의 외래 키 검사가 comments(상위 댓글)·articles 에 {@code FOR KEY SHARE} 를 먼저 걸고,
     * 그다음 카운터 트리거가 articles 배타 잠금을 요청합니다.
     * 관리자가 articles 를 들고 comments 를 기다리는 사이 사용자는 comments 를 들고 articles 를
     * 기다리게 되어 <b>사이클이 생깁니다.</b> 40P01 로 한쪽이 죽는데,
     * 희생자가 사용자면 아무 잘못 없는 답글 작성이 실패하고, 관리자면 200건이 통째로 롤백됩니다.
     *
     * <p>대상을 <b>먼저 전부</b> 잠그면 관리자는 articles 를 아직 하나도 들고 있지 않은 상태에서만
     * comments 를 기다립니다. 사용자는 첫 단계에서 막히므로 아무것도 들지 않은 채 대기합니다.
     * 어느 쪽도 상대가 원하는 것을 들고 있지 않아 사이클이 성립하지 않습니다.
     *
     * <p>잠금 순서 {@code (parent_id IS NULL, id)} 는 comments 전체에 대한 <b>전순서</b>라,
     * 두 관리자의 대상이 겹쳐도 겹친 부분을 같은 상대 순서로 잡습니다.
     *
     * <p>한 건씩 도는 이유는 {@code ORDER BY ... FOR UPDATE} 한 문장으로는 실제 잠금 획득 순서가
     * 실행 계획에 달려 있어 보장되지 않기 때문입니다.
     */
    private void lockAll(List<Long> orderedIds) {
        for (Long commentId : orderedIds) {
            commentRepository.lockById(commentId);
        }
    }

    /**
     * 한 건 삭제. 호출 시점에 이 행은 {@link #lockAll} 이 이미 잠가 두었습니다.
     *
     * <p>잠금이 판정보다 <b>먼저</b>여야 하는 이유는 답글입니다.
     * "답글 없음" 으로 판정한 직후 달린 답글은 {@code ON DELETE CASCADE} 로 함께 사라집니다.
     * 관리자는 댓글 하나를 지웠는데 남의 답글이 없어진 것도 모릅니다.
     * ({@code CommentRepository#lockById} 가 왜 native {@code FOR UPDATE} 인지도 그 주석에 있습니다)
     */
    private boolean deleteOne(Long commentId, Long actorId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(found -> !found.isDeleted())
                .orElse(null);
        if (comment == null) {
            return false;
        }

        boolean hasReplies = commentRepository.existsByParentId(commentId);
        log.warn("관리자 댓글 삭제. commentId={} articleId={} authorId={} 답글유지={} actorId={}",
                commentId, comment.getArticleId(), comment.getUserId(), hasReplies, actorId);

        if (hasReplies) {
            comment.softDelete();
        } else {
            commentRepository.delete(comment);
        }
        return true;
    }
}
