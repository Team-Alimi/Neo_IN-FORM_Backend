package today.inform.inform.admin.article.service;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.dto.response.BulkResult;
import today.inform.inform.admin.article.dto.response.MergeResult;
import today.inform.inform.admin.article.dto.response.SimilarComparison;
import today.inform.inform.admin.article.repository.AdminArticleQueryRepository;
import today.inform.inform.admin.article.repository.ArticleMergeRepository;
import today.inform.inform.admin.file.repository.AttachmentQueryRepository;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.storage.FileStorage;

/**
 * ADM-10 영구 삭제 · ADM-12 유사 비교 · ADM-13 병합.
 *
 * <p>셋 다 <b>되돌릴 수 없는</b> 조작이라 한곳에 모았습니다.
 * 나머지 관리자 기능은 상태를 옮기거나 내용을 고칠 뿐이고 언제든 되돌릴 수 있습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleMergeService {

    private final ArticleRepository articleRepository;
    private final ArticleMergeRepository mergeRepository;
    private final AdminArticleQueryRepository queryRepository;
    private final AdminArticleWriteService writeService;
    private final AttachmentQueryRepository attachmentQueryRepository;
    private final FileStorage fileStorage;
    private final BulkExecutor bulkExecutor;

    /**
     * ADM-12 유사 공지 비교.
     *
     * <p>비교 대상이 없으면 {@code similar} 가 비어 나갑니다 —
     * 아직 판정되지 않았거나 상대 공지가 이미 병합·삭제된 경우입니다.
     * 그 자체가 관리자에게 필요한 정보라 404 로 만들지 않습니다.
     */
    @Transactional(readOnly = true)
    public SimilarComparison compareWithSimilar(Long articleId) {
        AdminArticleDetail article = writeService.getDetail(articleId);

        AdminArticleDetail similar = (article.similarArticleId() == null)
                ? null
                : queryRepository.findDetail(article.similarArticleId()).orElse(null);

        return new SimilarComparison(article, similar, article.similarityScore());
    }

    /**
     * ADM-13 병합.
     *
     * <p><b>순서가 중요합니다.</b> 딸린 것을 전부 옮긴 <b>뒤</b> 지워야 합니다.
     * {@code articles} 를 참조하는 외래 키가 자기 참조 하나를 빼고 전부
     * {@code ON DELETE CASCADE} 라, 먼저 지우면 남은 것이 조용히 사라집니다.
     *
     * @return 실제로 흡수된 공지 수
     */
    @Transactional
    public MergeResult merge(Long targetId, List<Long> sourceIds, String memo, Long actorId) {
        List<Long> sources = sourceIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !id.equals(targetId))
                .toList();
        if (sources.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "흡수할 공지가 없습니다.");
        }

        Article target = load(targetId);
        requireMergeable(target);

        MovedCounter moved = new MovedCounter();
        for (Long sourceId : sources) {
            absorb(target, load(sourceId), actorId, memo, moved);
        }
        return new MergeResult(targetId, sources, moved.toResponse());
    }

    /**
     * 옮겨진 건수를 모읍니다.
     *
     * <p>병합은 되돌릴 수 없고 흡수된 공지는 사라집니다. 관리자가 그 자리에서
     * "예상한 만큼 옮겨졌나" 를 볼 수 없으면, 무언가 빠졌어도 알아챌 방법이 없습니다.
     */
    private static final class MovedCounter {
        private int vendors;
        private int bookmarks;
        private int attachments;
        private int categories;
        private int comments;
        private int notifications;

        MergeResult.Moved toResponse() {
            return new MergeResult.Moved(vendors, bookmarks, attachments, categories,
                    comments, notifications);
        }
    }

    /**
     * ADM-10 영구 삭제.
     *
     * <p><b>휴지통에 있는 공지만 지울 수 있습니다.</b> 되돌릴 수 없는 조작이라
     * "휴지통으로 보내기" 를 한 번 거치게 해서 실수의 여지를 줄입니다.
     * 목록에서 잘못 선택한 공지가 곧바로 사라지면 복구할 방법이 없습니다.
     *
     * <p><b>★ DB 에는 흔적이 남지 않습니다.</b> 공지를 지우면 상태 이력도 CASCADE 로 함께 사라져,
     * "누가 무엇을 지웠는지" 를 나중에 확인할 방법이 없습니다.
     * 관리자 조작 전용 감사 테이블이 없어서 지금은 애플리케이션 로그로만 남깁니다 —
     * 사용자 데이터를 영구히 지우는 기능치고는 약한 기록이라 <b>보완이 필요합니다.</b>
     *
     * <p><b>S3 객체도 함께 지웁니다.</b> v1 은 삭제 코드를 만들어 두고 <b>어디서도 부르지 않아</b>
     * 공지를 지워도 스토리지에 파일이 그대로 남았습니다. 그 경로가 여기입니다.
     *
     * @return 지운 개수
     */
    public BulkResult deletePermanently(List<Long> articleIds) {
        return bulkExecutor.runEach(articleIds, this::deleteOnePermanently);
    }

    /** 한 건 = 한 트랜잭션. 나머지 건에 영향을 주지 않습니다. */
    private void deleteOnePermanently(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        if (article.getStatus() != ArticleStatus.TRASHED) {
            throw new BusinessException(
                    ErrorCode.NOT_IN_TRASH,
                    "휴지통에 있는 공지만 영구 삭제할 수 있습니다. articleId=" + articleId);
        }

        // ★ 지우기 전에 읽어야 합니다. attachments 가 ON DELETE CASCADE 라
        //   공지를 지우는 순간 어떤 객체를 지워야 하는지 알 방법이 사라집니다.
        List<String> objectKeys = attachmentQueryRepository.findS3ObjectKeys(List.of(articleId));

        // ★ 지우기 전에 기록합니다. 지운 뒤에는 남길 곳이 없습니다 —
        //   article_status_logs 도 CASCADE 로 함께 사라지므로 DB 에는 흔적이 하나도 안 남습니다.
        //   지금은 애플리케이션 로그가 유일한 기록입니다.
        //   관리자 조작 전용 감사 테이블이 생기면 그쪽으로 옮겨야 합니다.
        log.warn("공지 영구 삭제. articleId={} status={} title={} 첨부={}",
                article.getId(), article.getStatus(), article.getTitle(), objectKeys.size());

        articleRepository.delete(article);
        deleteObjectsAfterCommit(objectKeys);
    }

    /**
     * S3 삭제를 <b>커밋 이후로</b> 미룹니다.
     *
     * <p>트랜잭션 안에서 지우면 이후 롤백됐을 때 <b>공지는 살아 있는데 이미지만 사라진</b>
     * 상태가 됩니다. 되돌릴 방법이 없습니다.
     *
     * <p>반대로 커밋 뒤에 지우다 실패하면 고아 객체가 남지만, 그건 비용 문제일 뿐
     * 사용자에게 깨진 화면을 보이지는 않습니다. 두 실패 중 이쪽이 낫습니다.
     * ({@code FileStorage#deleteAll} 은 실패해도 예외를 던지지 않고 로그만 남깁니다)
     */
    private void deleteObjectsAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorage.deleteAll(objectKeys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorage.deleteAll(objectKeys);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 딸린 것을 전부 옮기고 흡수된 공지를 지웁니다.
     *
     * <p>여기 나열된 것이 곧 "잃으면 안 되는 것" 의 전부입니다.
     * 하나라도 빠지면 그 데이터는 오류 없이 사라집니다 —
     * 새 테이블이 {@code articles} 를 참조하게 되면 <b>여기에도 반드시 추가</b>해야 합니다.
     */
    private void absorb(Article target, Article source, Long actorId, String memo, MovedCounter moved) {
        requireSameSourceType(target, source);

        Long targetId = target.getId();
        Long sourceId = source.getId();

        moved.vendors += mergeRepository.moveVendors(targetId, sourceId);
        moved.categories += mergeRepository.mergeCategories(targetId, sourceId);
        moved.attachments += mergeRepository.moveAttachments(targetId, sourceId);
        moved.bookmarks += mergeRepository.moveUserReactions(targetId, sourceId);
        moved.comments += mergeRepository.moveComments(targetId, sourceId);
        moved.notifications += mergeRepository.moveNotifications(targetId, sourceId);
        mergeRepository.moveStatusLogs(targetId, sourceId);

        mergeRepository.recordMerge(targetId, sourceId, actorId, memo);

        // ★ 지우기 직전에. FK 가 SET NULL 이라 지운 뒤에는 어느 공지가 소스를 가리켰는지 알 수 없습니다.
        mergeRepository.clearSimilarityPointingAt(sourceId);
        mergeRepository.deleteArticle(sourceId);
    }

    /**
     * 출처 유형이 다르면 병합할 수 없습니다.
     *
     * <p>학교 공지에 동아리 제공처를 옮기는 순간 무결성 트리거가 IN002 로 막습니다.
     * 여기서 먼저 걸러 내지 않으면 <b>일부만 옮겨진 채</b> 트랜잭션이 뒤집혀,
     * 관리자는 무엇이 문제였는지 알 수 없는 오류만 받습니다.
     */
    private void requireSameSourceType(Article target, Article source) {
        if (target.getSourceType() != source.getSourceType()) {
            throw new BusinessException(
                    ErrorCode.VENDOR_TYPE_MISMATCH,
                    "출처 유형이 다른 공지는 병합할 수 없습니다. articleId=" + source.getId());
        }
    }

    /** 휴지통에 있는 공지로 흡수하면 사용자에게 보이지 않는 곳으로 데이터가 사라집니다. */
    private void requireMergeable(Article target) {
        if (target.getStatus() == ArticleStatus.TRASHED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION, "휴지통에 있는 공지로는 병합할 수 없습니다.");
        }
    }

    private Article load(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }
}
