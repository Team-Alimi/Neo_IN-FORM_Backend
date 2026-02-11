package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.dto.SchoolArticleResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolArticleService {

    private final SchoolArticleRepository schoolArticleRepository;
    private final SchoolArticleVendorRepository schoolArticleVendorRepository;
    private final AttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public SchoolArticleDetailResponse getSchoolArticleDetail(Integer articleId) {
        SchoolArticle article = schoolArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공지사항입니다."));

        List<SchoolArticleVendor> vendors = schoolArticleVendorRepository.findAllByArticle(article);
        var attachments = attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL);
        LocalDate today = LocalDate.now();

        return SchoolArticleDetailResponse.builder()
                .article_id(article.getArticleId())
                .title(article.getTitle())
                .content(article.getContent())
                .start_date(article.getStartDate())
                .due_date(article.getDueDate())
                .status(determineStatus(article, today))
                .created_at(article.getCreatedAt())
                .updated_at(article.getUpdatedAt())
                .categories(article.getCategory() == null ? null : SchoolArticleDetailResponse.CategoryResponse.builder()
                        .category_id(article.getCategory().getCategoryId())
                        .category_name(article.getCategory().getCategoryName())
                        .build())
                .vendors(vendors.stream()
                        .map(sav -> SchoolArticleDetailResponse.VendorResponse.builder()
                                .vendor_name(sav.getVendor().getVendorName())
                                .vendor_initial(sav.getVendor().getVendorInitial())
                                .vendor_type(sav.getVendor().getVendorType().name())
                                .original_url(sav.getOriginalUrl())
                                .build())
                        .collect(Collectors.toList()))
                .attachments(attachments.stream()
                        .map(att -> SchoolArticleDetailResponse.AttachmentResponse.builder()
                                .file_id(att.getId())
                                .attachment_url(att.getAttachmentUrl())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public SchoolArticleListResponse getSchoolArticles(Integer page, Integer size, Integer categoryId, String keyword) {
        // 보안/최적화: 최대 페이지 사이즈 제한
        int cappedSize = Math.min(size, 50);
        
        LocalDate today = LocalDate.now();
        LocalDate upcomingLimit = today.plusDays(5);
        LocalDate endingSoonLimit = today.plusDays(5);

        Pageable pageable = PageRequest.of(page - 1, cappedSize);
        Page<SchoolArticle> articlePage = schoolArticleRepository.findAllWithFiltersAndSorting(
                categoryId, keyword, today, upcomingLimit, endingSoonLimit, pageable
        );

        List<SchoolArticle> articles = articlePage.getContent();
        
        // 최적화: 게시글이 없으면 벤더 조회 생략
        if (articles.isEmpty()) {
            return SchoolArticleListResponse.builder()
                    .page_info(SchoolArticleListResponse.PageInfo.builder()
                            .current_page(page)
                            .total_pages(articlePage.getTotalPages())
                            .total_articles(articlePage.getTotalElements())
                            .has_next(articlePage.hasNext())
                            .build())
                    .school_articles(List.of())
                    .build();
        }

        // 벤더 정보 일괄 조회
        List<SchoolArticleVendor> savs = schoolArticleVendorRepository.findAllByArticleIn(articles);
        Map<Integer, List<SchoolArticleVendor>> vendorMap = savs.stream()
                .collect(Collectors.groupingBy(sav -> sav.getArticle().getArticleId()));

        List<SchoolArticleResponse> responseList = articles.stream()
                .map(article -> convertToResponse(article, vendorMap.get(article.getArticleId()), today))
                .collect(Collectors.toList());

        return SchoolArticleListResponse.builder()
                .page_info(SchoolArticleListResponse.PageInfo.builder()
                        .current_page(page)
                        .total_pages(articlePage.getTotalPages())
                        .total_articles(articlePage.getTotalElements())
                        .has_next(articlePage.hasNext())
                        .build())
                .school_articles(responseList)
                .build();
    }

    private SchoolArticleResponse convertToResponse(SchoolArticle article, List<SchoolArticleVendor> vendors, LocalDate today) {
        return SchoolArticleResponse.builder()
                .article_id(article.getArticleId())
                .title(article.getTitle())
                .start_date(article.getStartDate())
                .due_date(article.getDueDate())
                .status(determineStatus(article, today))
                .created_at(article.getCreatedAt())
                .updated_at(article.getUpdatedAt())
                .categories(article.getCategory() == null ? null : SchoolArticleResponse.CategoryResponse.builder()
                        .category_id(article.getCategory().getCategoryId())
                        .category_name(article.getCategory().getCategoryName())
                        .build())
                .vendors(vendors == null ? List.of() : vendors.stream()
                        .map(sav -> SchoolArticleResponse.VendorResponse.builder()
                                .vendor_name(sav.getVendor().getVendorName())
                                .vendor_initial(sav.getVendor().getVendorInitial())
                                .vendor_type(sav.getVendor().getVendorType().name())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private String determineStatus(SchoolArticle article, LocalDate today) {
        if (article.getDueDate() != null && article.getDueDate().isBefore(today)) return "CLOSED";
        if (article.getStartDate() != null && article.getStartDate().isAfter(today)) return "UPCOMING";
        if (article.getDueDate() != null && !article.getDueDate().isAfter(today.plusDays(5))) return "ENDING_SOON";
        return "OPEN";
    }
}
