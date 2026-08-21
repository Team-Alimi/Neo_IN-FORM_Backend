package today.inform.inform.admin.article.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest;
import today.inform.inform.admin.article.dto.request.SaveArticleRequest.AttachmentLink;
import today.inform.inform.admin.article.repository.AdminArticleWriteRepository.ResolvedAttachment;
import today.inform.inform.admin.article.dto.response.AdminArticleDetail;
import today.inform.inform.admin.article.repository.AdminArticleQueryRepository;
import today.inform.inform.admin.article.repository.AdminArticleWriteRepository;
import today.inform.inform.article.entity.Article;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.storage.FileStorage;

/**
 * ADM-04 상세 · ADM-05 수정 · ADM-06 생성.
 *
 * <p>검수 파이프라인({@code AdminArticleService})과 나눈 이유는 다루는 것이 다르기 때문입니다.
 * 저쪽은 <b>상태</b>를 옮기고 이쪽은 <b>내용</b>을 씁니다.
 * 상태를 여기서 바꾸지 않는 것도 그래서입니다 — 상태 변경은 전이 규칙과 감사 이력을 타야 하므로
 * {@code PATCH /admin/articles/status} 한 곳으로만 갑니다.
 */
@Service
@RequiredArgsConstructor
public class AdminArticleWriteService {

    private final ArticleRepository articleRepository;
    private final AdminArticleWriteRepository writeRepository;
    private final AdminArticleQueryRepository queryRepository;
    private final FileStorage fileStorage;

    /** ADM-04 상세. 상태를 가리지 않습니다. */
    @Transactional(readOnly = true)
    public AdminArticleDetail getDetail(Long articleId) {
        return queryRepository.findDetail(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    /**
     * ADM-06 게시글 번호 중복 확인.
     *
     * <p><b>이 결과는 보장이 아닙니다.</b> 두 관리자가 같은 번호를 확인하면 둘 다 "사용 가능" 을 받고,
     * 저장에서 한 명이 실패합니다. 최종 판정은 저장 시점의 PK 제약(409)입니다.
     * 잠금을 걸어 보장하려면 확인 시점부터 저장까지 번호를 붙들고 있어야 하는데,
     * 관리자가 글을 쓰는 동안 내내 잠그는 셈이라 얻는 것보다 잃는 것이 큽니다.
     */
    @Transactional(readOnly = true)
    public boolean isIdAvailable(Long articleId) {
        return !writeRepository.existsById(articleId);
    }

    /**
     * ADM-06 생성.
     *
     * <p>번호를 지정했으면 native INSERT 를 쓰고 <b>시퀀스를 밀어올립니다.</b>
     * 그러지 않으면 나중에 크롤러가 그 번호에 도달했을 때 수집이 통째로 실패합니다
     * ({@code AdminArticleWriteRepository#bumpSequence} 참조).
     */
    @Transactional
    public Long create(SaveArticleRequest request, Long adminId) {
        SourceType sourceType = request.sourceType() == null ? SourceType.SCHOOL : request.sourceType();
        ArticleStatus status = request.status() == null ? defaultStatus(sourceType) : request.status();
        requireCreatable(status, request);
        requireValidManualId(request);

        Long articleId = (request.articleId() == null)
                ? createWithGeneratedId(sourceType, status, request, adminId)
                : createWithManualId(sourceType, status, request, adminId);

        applyRelations(articleId, request);
        return articleId;
    }

    /**
     * ADM-05 수정.
     *
     * <p><b>바꿀 수 있는 것은 내용·기간·분류·출처·첨부뿐입니다.</b>
     * 출처 유형은 트리거가 IN001 로 막고, 상태는 전이 규칙을 타야 해서 여기서 다루지 않습니다.
     * 요청에 그 값들이 들어와도 조용히 무시합니다 — 거부하면 화면이 전체 폼을 보내는
     * 흔한 구현에서 매번 400 이 나기 때문입니다.
     *
     * <p>제목·본문·기간이 실제로 바뀌면 DB 가 AI 요약을 무효화하고 수정 시각을 올립니다.
     * 앱은 따로 할 일이 없습니다.
     *
     * <p><b>★ 부분 수정입니다</b>(명세 4.8). 보낸 필드만 반영합니다.
     * {@code categoryIds}·{@code vendors}·{@code attachments} 는
     * <b>보내면 전체 교체, 안 보내면 유지</b>입니다.
     * 생략이 곧 유지라서, 제목 오타 하나 고치려고 저장한 요청이 분류를 통째로 지우는 일이 없습니다.
     * 비우려면 빈 배열을 <b>명시적으로</b> 보내야 합니다 — 그 둘은 다른 뜻입니다.
     */
    @Transactional
    public AdminArticleDetail update(Long articleId, SaveArticleRequest request) {
        requireValidManualId(request);

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // ★ null 은 "그대로 두기" 입니다. 그냥 넘기면 제목 하나 고치려던 요청이
        //   기간을 통째로 지웁니다 — 오류 없이 사라지므로 관리자는 알아채지 못합니다.
        article.edit(
                orKeep(request.title(), article.getTitle()),
                orKeep(request.content(), article.getContent()),
                orKeep(request.startsOn(), article.getStartsOn()),
                orKeep(request.endsOn(), article.getEndsOn()));

        applyRelations(articleId, request);
        return getDetail(articleId);
    }

    /**
     * 관계 세 가지를 반영합니다. <b>{@code null} 은 "그대로 두기" 입니다.</b>
     *
     * <p>생성에서는 세 값이 전부 {@code null} 일 수 있고, 그건 "아무것도 안 붙임" 과 같습니다 —
     * 새 공지에는 유지할 기존 관계가 없기 때문입니다.
     */
    private static <T> T orKeep(T requested, T current) {
        return requested == null ? current : requested;
    }

    private void applyRelations(Long articleId, SaveArticleRequest request) {
        if (request.categoryIds() != null) {
            writeRepository.replaceCategories(articleId, request.categoryIds());
        }
        if (request.vendors() != null) {
            writeRepository.syncVendors(articleId, request.vendors());
        }
        if (request.attachments() != null) {
            writeRepository.syncAttachments(articleId, resolve(request.attachments()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 요청의 첨부를 저장 형식으로 해석합니다.
     *
     * <p><b>{@code storage_type} 을 클라이언트가 정하지 않습니다.</b> 주소가 우리 버킷 것인지를
     * 서버가 판정합니다. 클라이언트 말을 믿으면 남의 주소를 S3 첨부로 등록해 두었다가
     * 공지를 지울 때 <b>우리 버킷의 엉뚱한 객체를 지우게</b> 만들 수 있습니다.
     *
     * <p>{@code ck_attachments_object_key} 가 {@code (storage_type='S3') = (object_key IS NOT NULL)}
     * 를 강제하므로 두 값은 반드시 짝으로 정해집니다.
     */
    private List<ResolvedAttachment> resolve(List<AttachmentLink> attachments) {
        return attachments.stream().map(this::resolve).toList();
    }

    private ResolvedAttachment resolve(AttachmentLink attachment) {
        String objectKey = fileStorage.objectKeyOf(attachment.fileUrl()).orElse(null);
        return new ResolvedAttachment(
                attachment.id(),
                attachment.fileUrl(),
                objectKey == null ? "EXTERNAL" : "S3",
                objectKey,
                attachment.originalName(),
                attachment.contentType(),
                attachment.sizeBytes());
    }

    private Long createWithGeneratedId(SourceType sourceType, ArticleStatus status,
                                       SaveArticleRequest request, Long adminId) {
        Article article = articleRepository.save(Article.createWithStatus(
                sourceType, status,
                request.title(), request.content(),
                request.startsOn(), request.endsOn(), adminId));
        return article.getId();
    }

    /**
     * 번호를 직접 지정하는 경로.
     *
     * <p>JPA 를 쓸 수 없어 native INSERT 입니다. 그래서 엔티티의 검증을 못 거치므로
     * 같은 규칙을 여기서 한 번 확인합니다 — 검증이 두 곳에 생기는 건 좋지 않지만,
     * 통과시키면 DB 가 23514 로 뭉뚱그려 거부해 어느 값이 문제인지 알 수 없게 됩니다.
     */
    private Long createWithManualId(SourceType sourceType, ArticleStatus status,
                                    SaveArticleRequest request, Long adminId) {
        if (!status.isAllowedFor(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_SOURCE);
        }
        if (request.startsOn() != null && request.endsOn() != null
                && request.startsOn().isAfter(request.endsOn())) {
            throw new BusinessException(ErrorCode.INVALID_ARTICLE_PERIOD);
        }

        Long articleId = request.articleId();
        writeRepository.insertWithId(articleId, sourceType, status,
                request.title(), request.content(),
                request.startsOn(), request.endsOn(), adminId);

        // ★ INSERT 가 성공한 뒤에 밀어올립니다. 먼저 밀면 중복으로 실패했을 때
        //   쓰이지도 않은 번호만큼 시퀀스가 건너뛰어집니다.
        writeRepository.bumpSequence(articleId);
        return articleId;
    }

    /**
     * 수동 지정 번호의 상한을 확인합니다.
     *
     * <p>Bean Validation 이 컨트롤러에서 먼저 막지만 여기서 한 번 더 봅니다 —
     * 이 서비스는 나중에 배치나 관리 도구에서도 불릴 수 있고, 그 경로는 검증을 안 거칩니다.
     */
    private static void requireValidManualId(SaveArticleRequest request) {
        if (request.articleId() != null && request.articleId() > SaveArticleRequest.MAX_MANUAL_ID) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "게시글 번호가 너무 큽니다.");
        }
    }

    /**
     * 휴지통 상태로는 만들 수 없습니다.
     *
     * <p>복구는 <b>감사 이력의 직전 상태</b>로만 갑니다. 처음부터 휴지통으로 만들면
     * {@code to_status='TRASHED'} 인 이력의 {@code from_status} 가 NULL 이라
     * 복구할 곳이 없습니다. <b>영영 꺼낼 수 없는 공지</b>가 만들어지고,
     * 목록에도 안 뜨니 관리자는 그런 게 있는 줄도 모릅니다.
     */
    private static void requireCreatable(ArticleStatus status, SaveArticleRequest request) {
        // 생성에는 제목·본문이 반드시 있어야 합니다. DTO 에는 @NotBlank 를 걸 수 없습니다 —
        // 같은 DTO 를 PATCH 가 쓰는데 거기서는 "안 보냄" 이 정상이기 때문입니다.
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "제목을 입력해 주세요.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "본문을 입력해 주세요.");
        }
        if (status == ArticleStatus.TRASHED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_FOR_SOURCE, "휴지통 상태로는 공지를 만들 수 없습니다.");
        }
    }

    private static ArticleStatus defaultStatus(SourceType sourceType) {
        return sourceType == SourceType.CLUB ? ArticleStatus.DRAFT : ArticleStatus.PENDING_REVIEW;
    }
}
