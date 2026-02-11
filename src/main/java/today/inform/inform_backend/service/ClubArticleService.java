package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.dto.ClubArticleDetailResponse;
import today.inform.inform_backend.dto.ClubArticleListResponse;
import today.inform.inform_backend.dto.ClubArticleResponse;
import today.inform.inform_backend.entity.Attachment;
import today.inform.inform_backend.entity.ClubArticle;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.ClubArticleRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubArticleService {

    private final ClubArticleRepository clubArticleRepository;
    private final AttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public ClubArticleDetailResponse getClubArticleDetail(Integer articleId) {
        ClubArticle article = clubArticleRepository.findByIdWithVendor(articleId)
                .orElseThrow(() -> new today.inform.inform_backend.common.exception.BusinessException("ARTICLE_NOT_FOUND", "존재하지 않는 동아리 공지사항입니다."));

        List<Attachment> attachments = attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.CLUB);

        return ClubArticleDetailResponse.builder()
                .article_id(article.getArticleId())
                .title(article.getTitle())
                .content(article.getContent())
                .original_url(article.getOriginalUrl())
                .start_date(article.getStartDate())
                .due_date(article.getDueDate())
                .created_at(article.getCreatedAt())
                .updated_at(article.getUpdatedAt())
                .attachments(attachments.stream()
                        .map(att -> ClubArticleDetailResponse.AttachmentResponse.builder()
                                .file_id(att.getId())
                                .file_url(att.getAttachmentUrl())
                                .build())
                        .collect(Collectors.toList()))
                .vendors(ClubArticleDetailResponse.VendorResponse.builder()
                        .vendor_id(article.getVendor().getVendorId())
                        .vendor_name(article.getVendor().getVendorName())
                        .vendor_initial(article.getVendor().getVendorInitial())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public ClubArticleListResponse getClubArticles(Integer page, Integer size, Integer vendorId) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ClubArticle> articlePage = clubArticleRepository.findAllWithFilters(vendorId, pageable);

        List<ClubArticle> articles = articlePage.getContent();
        List<Integer> articleIds = articles.stream()
                .map(ClubArticle::getArticleId)
                .collect(Collectors.toList());

        // 각 게시글의 첫 번째 첨부파일만 가져오기 위한 로직 (N+1 방지)
        Map<Integer, String> firstAttachmentMap = attachmentRepository
                .findAllByArticleIdInAndArticleType(articleIds, VendorType.CLUB)
                .stream()
                .collect(Collectors.groupingBy(
                        Attachment::getArticleId,
                        Collectors.mapping(Attachment::getAttachmentUrl, Collectors.collectingAndThen(Collectors.toList(), list -> list.isEmpty() ? null : list.get(0)))
                ));

        List<ClubArticleResponse> responseList = articles.stream()
                .map(article -> ClubArticleResponse.builder()
                        .article_id(article.getArticleId())
                        .title(article.getTitle())
                        .start_date(article.getStartDate())
                        .due_date(article.getDueDate())
                        .created_at(article.getCreatedAt())
                        .updated_at(article.getUpdatedAt())
                        .attachment_url(firstAttachmentMap.get(article.getArticleId()))
                        .vendors(ClubArticleResponse.VendorResponse.builder()
                                .vendor_name(article.getVendor().getVendorName())
                                .vendor_initial(article.getVendor().getVendorInitial())
                                .build())
                        .build())
                .collect(Collectors.toList());

        return ClubArticleListResponse.builder()
                .page_info(ClubArticleListResponse.PageInfo.builder()
                        .current_page(page)
                        .total_pages(articlePage.getTotalPages())
                        .total_articles(articlePage.getTotalElements())
                        .build())
                .club_articles(responseList)
                .build();
    }
}
