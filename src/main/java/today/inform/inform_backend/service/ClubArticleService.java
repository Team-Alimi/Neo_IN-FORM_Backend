package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
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
    private final today.inform.inform_backend.repository.BookmarkRepository bookmarkRepository;
    private final today.inform.inform_backend.repository.UserRepository userRepository;

    @Transactional(readOnly = true)
    public ClubArticleDetailResponse getClubArticleDetail(Integer articleId, Integer userId) {
        ClubArticle article = clubArticleRepository.findByIdWithVendor(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        boolean isBookmarked = false;
        if (userId != null) {
            today.inform.inform_backend.entity.User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                isBookmarked = bookmarkRepository.existsByUserAndArticleTypeAndArticleId(user, VendorType.CLUB, articleId);
            }
        }

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
                .is_bookmarked(isBookmarked)
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
        // 보안/최적화: 최대 페이지 사이즈 제한 (예: 50)
        int cappedSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page - 1, cappedSize);
        Page<ClubArticle> articlePage = clubArticleRepository.findAllWithFilters(vendorId, pageable);

        List<ClubArticle> articles = articlePage.getContent();
        
        // 최적화: 게시글이 없으면 첨부파일 쿼리 생략
        if (articles.isEmpty()) {
            return ClubArticleListResponse.builder()
                    .page_info(ClubArticleListResponse.PageInfo.builder()
                            .current_page(page)
                            .total_pages(articlePage.getTotalPages())
                            .total_articles(articlePage.getTotalElements())
                            .build())
                    .club_articles(List.of())
                    .build();
        }

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
