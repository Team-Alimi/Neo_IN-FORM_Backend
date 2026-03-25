package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.dto.SchoolArticleResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.repository.CategoryRepository;
import today.inform.inform_backend.repository.VendorRepository;
import today.inform.inform_backend.entity.Category;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.Attachment;
import today.inform.inform_backend.dto.AdminArticleCreateRequest;
import today.inform.inform_backend.dto.AdminUnifiedDetailResponse;
import today.inform.inform_backend.dto.AdminUnifiedUpdateRequest;

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
        private final today.inform.inform_backend.repository.BookmarkRepository bookmarkRepository;
        private final today.inform.inform_backend.repository.UserRepository userRepository;
        private final CategoryRepository categoryRepository;
        private final VendorRepository vendorRepository;

        @Transactional(readOnly = true)
        public SchoolArticleDetailResponse getSchoolArticleDetail(Integer articleId, Integer userId) {
                SchoolArticle article = schoolArticleRepository.findById(articleId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

                boolean isBookmarked = false;
                String content = article.getContent();

                // 로그인 사용자인 경우 북마크 여부 확인
                if (userId != null) {
                        today.inform.inform_backend.entity.User user = userRepository.findById(userId)
                                        .orElse(null);

                        if (user != null) {
                                isBookmarked = bookmarkRepository.existsByUserAndArticleTypeAndArticleId(user,
                                                VendorType.SCHOOL, articleId);
                        }
                }

                long bookmarkCount = bookmarkRepository.countByArticleIdAndArticleType(articleId, VendorType.SCHOOL);
                List<SchoolArticleVendor> vendors = schoolArticleVendorRepository.findAllByArticle(article);
                var attachments = attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL);
                LocalDate todayDate = LocalDate.now();

                return SchoolArticleDetailResponse.builder()
                                .articleId(article.getArticleId())
                                .title(article.getTitle())
                                .content(content)
                                .startDate(article.getStartDate())
                                .dueDate(article.getDueDate())
                                .status(determineStatus(article, todayDate))
                                .createdAt(article.getCreatedAt())
                                .updatedAt(article.getUpdatedAt())
                                .isBookmarked(isBookmarked)
                                .bookmarkCount(bookmarkCount)
                                .categories(article.getCategory() == null ? null
                                                : SchoolArticleDetailResponse.CategoryResponse.builder()
                                                                .categoryId(article.getCategory().getCategoryId())
                                                                .categoryName(article.getCategory().getCategoryName())
                                                                .build())
                                .vendors(vendors.stream()
                                                .map(sav -> SchoolArticleDetailResponse.VendorResponse.builder()
                                                                .vendorId(sav.getVendor().getVendorId())
                                                                .vendorName(sav.getVendor().getVendorName())
                                                                .vendorInitial(sav.getVendor().getVendorInitial())
                                                                .vendorType(sav.getVendor().getVendorType().name())
                                                                .originalUrl(sav.getOriginalUrl())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .attachments(attachments.stream()
                                                .map(att -> SchoolArticleDetailResponse.AttachmentResponse.builder()
                                                                .fileId(att.getId())
                                                                .attachmentUrl(att.getAttachmentUrl())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .build();
        }

        @Transactional(readOnly = true)
        @Cacheable(value = "hotArticles", key = "#userId ?: 'anonymous'", unless = "#result == null")
        public List<SchoolArticleResponse> getHotSchoolArticles(Integer userId) {
                LocalDate todayDate = LocalDate.now();
                List<SchoolArticle> articles = schoolArticleRepository.findHotArticles(todayDate, 10);

                if (articles.isEmpty()) {
                        return List.of();
                }

                List<Integer> articleIds = articles.stream()
                                .map(SchoolArticle::getArticleId)
                                .collect(Collectors.toList());

                // 북마크 여부 일괄 확인
                java.util.Set<Integer> bookmarkedIdsResult = new java.util.HashSet<>();
                if (userId != null) {
                        today.inform.inform_backend.entity.User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                                bookmarkedIdsResult = bookmarkRepository
                                                .findAllByUserAndArticleTypeAndArticleIdIn(user, VendorType.SCHOOL,
                                                                articleIds)
                                                .stream()
                                                .map(today.inform.inform_backend.entity.Bookmark::getArticleId)
                                                .collect(java.util.stream.Collectors.toSet());
                        }
                }
                final java.util.Set<Integer> finalBookmarkedIds = bookmarkedIdsResult;

                List<SchoolArticleVendor> savs = schoolArticleVendorRepository.findAllByArticleIn(articles);
                Map<Integer, List<SchoolArticleVendor>> vendorMap = savs.stream()
                                .collect(Collectors.groupingBy(sav -> sav.getArticle().getArticleId()));

                Map<Integer, Long> bookmarkCountMap = getBookmarkCountMap(articleIds);

                return articles.stream()
                                .map(article -> convertToResponse(article, vendorMap.get(article.getArticleId()),
                                                todayDate, finalBookmarkedIds.contains(article.getArticleId()),
                                                bookmarkCountMap.get(article.getArticleId())))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public SchoolArticleListResponse getSchoolArticlesByIds(List<Integer> articleIds, List<Integer> categoryIds,
                        String keyword, LocalDate startDate, LocalDate endDate, String status, Integer page,
                        Integer size, Integer userId) {
                int cappedSize = Math.min(size, 50);
                LocalDate todayDate = LocalDate.now();
                LocalDate upcomingLimit = todayDate.plusDays(5);
                LocalDate endingSoonLimit = todayDate.plusDays(5);

                Pageable pageable = PageRequest.of(page - 1, cappedSize);
                Page<SchoolArticle> articlePage = schoolArticleRepository.findAllByIdsWithFiltersAndSorting(
                                articleIds, categoryIds, keyword, startDate, endDate, status, todayDate, upcomingLimit,
                                endingSoonLimit, pageable);

                List<SchoolArticle> articles = articlePage.getContent();
                if (articles.isEmpty()) {
                        return SchoolArticleListResponse.builder()
                                        .pageInfo(SchoolArticleListResponse.PageInfo.builder()
                                                        .currentPage(page)
                                                        .totalPages(articlePage.getTotalPages())
                                                        .totalArticles(articlePage.getTotalElements())
                                                        .hasNext(articlePage.hasNext())
                                                        .build())
                                        .schoolArticles(List.of())
                                        .build();
                }

                // 북마크 여부 일괄 확인
                java.util.Set<Integer> bookmarkedIdsResult = new java.util.HashSet<>();
                if (userId != null) {
                        today.inform.inform_backend.entity.User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                                bookmarkedIdsResult = bookmarkRepository
                                                .findAllByUserAndArticleTypeAndArticleIdIn(user, VendorType.SCHOOL,
                                                                articleIds)
                                                .stream()
                                                .map(today.inform.inform_backend.entity.Bookmark::getArticleId)
                                                .collect(java.util.stream.Collectors.toSet());
                        }
                }
                final java.util.Set<Integer> finalBookmarkedIds = bookmarkedIdsResult;

                List<SchoolArticleVendor> savs = schoolArticleVendorRepository.findAllByArticleIn(articles);
                Map<Integer, List<SchoolArticleVendor>> vendorMap = savs.stream()
                                .collect(Collectors.groupingBy(sav -> sav.getArticle().getArticleId()));

                Map<Integer, Long> bookmarkCountMap = getBookmarkCountMap(articleIds);

                List<SchoolArticleResponse> responseList = articles.stream()
                                .map(article -> convertToResponse(article, vendorMap.get(article.getArticleId()),
                                                todayDate, finalBookmarkedIds.contains(article.getArticleId()),
                                                bookmarkCountMap.get(article.getArticleId())))
                                .collect(Collectors.toList());

                return SchoolArticleListResponse.builder()
                                .pageInfo(SchoolArticleListResponse.PageInfo.builder()
                                                .currentPage(page)
                                                .totalPages(articlePage.getTotalPages())
                                                .totalArticles(articlePage.getTotalElements())
                                                .hasNext(articlePage.hasNext())
                                                .build())
                                .schoolArticles(responseList)
                                .build();
        }

        @Transactional(readOnly = true)
        public SchoolArticleListResponse getSchoolArticles(Integer page, Integer size, List<Integer> categoryIds,
                        List<Integer> vendorIds, String keyword, LocalDate startDate, LocalDate endDate, String status,
                        Integer userId) {
                // 보안/최적화: 최대 페이지 사이즈 제한
                int cappedSize = Math.min(size, 50);

                LocalDate todayDate = LocalDate.now();
                LocalDate upcomingLimit = todayDate.plusDays(5);
                LocalDate endingSoonLimit = todayDate.plusDays(5);

                Pageable pageable = PageRequest.of(page - 1, cappedSize);
                Page<SchoolArticle> articlePage = schoolArticleRepository.findAllWithFiltersAndSorting(
                                categoryIds, vendorIds, keyword, startDate, endDate, status, todayDate, upcomingLimit,
                                endingSoonLimit, pageable);

                List<SchoolArticle> articles = articlePage.getContent();

                // 최적화: 게시글이 없으면 빈 응답
                if (articles.isEmpty()) {
                        return SchoolArticleListResponse.builder()
                                        .pageInfo(SchoolArticleListResponse.PageInfo.builder()
                                                        .currentPage(page)
                                                        .totalPages(articlePage.getTotalPages())
                                                        .totalArticles(articlePage.getTotalElements())
                                                        .hasNext(articlePage.hasNext())
                                                        .build())
                                        .schoolArticles(List.of())
                                        .build();
                }

                List<Integer> articleIds = articles.stream()
                                .map(SchoolArticle::getArticleId)
                                .collect(Collectors.toList());

                // 북마크 여부 일괄 확인
                java.util.Set<Integer> bookmarkedIdsResult = new java.util.HashSet<>();
                if (userId != null) {
                        today.inform.inform_backend.entity.User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                                bookmarkedIdsResult = bookmarkRepository
                                                .findAllByUserAndArticleTypeAndArticleIdIn(user, VendorType.SCHOOL,
                                                                articleIds)
                                                .stream()
                                                .map(today.inform.inform_backend.entity.Bookmark::getArticleId)
                                                .collect(java.util.stream.Collectors.toSet());
                        }
                }
                final java.util.Set<Integer> finalBookmarkedIds = bookmarkedIdsResult;

                // 벤더 정보 일괄 조회
                List<SchoolArticleVendor> savs = schoolArticleVendorRepository.findAllByArticleIn(articles);
                Map<Integer, List<SchoolArticleVendor>> vendorMap = savs.stream()
                                .collect(Collectors.groupingBy(sav -> sav.getArticle().getArticleId()));

                // 북마크 개수 일괄 조회
                Map<Integer, Long> bookmarkCountMap = getBookmarkCountMap(articleIds);

                List<SchoolArticleResponse> responseList = articles.stream()
                                .map(article -> convertToResponse(article, vendorMap.get(article.getArticleId()),
                                                todayDate, finalBookmarkedIds.contains(article.getArticleId()),
                                                bookmarkCountMap.get(article.getArticleId())))
                                .collect(Collectors.toList());

                return SchoolArticleListResponse.builder()
                                .pageInfo(SchoolArticleListResponse.PageInfo.builder()
                                                .currentPage(page)
                                                .totalPages(articlePage.getTotalPages())
                                                .totalArticles(articlePage.getTotalElements())
                                                .hasNext(articlePage.hasNext())
                                                .build())
                                .schoolArticles(responseList)
                                .build();
        }

        SchoolArticleResponse convertToResponse(SchoolArticle article, List<SchoolArticleVendor> vendors,
                        LocalDate today, boolean isBookmarked, Long bookmarkCount) {
                return SchoolArticleResponse.builder()
                                .articleId(article.getArticleId())
                                .title(article.getTitle())
                                .startDate(article.getStartDate())
                                .dueDate(article.getDueDate())
                                .status(determineStatus(article, today))
                                .createdAt(article.getCreatedAt())
                                .updatedAt(article.getUpdatedAt())
                                .isBookmarked(isBookmarked)
                                .bookmarkCount(bookmarkCount != null ? bookmarkCount : 0L)
                                .categories(article.getCategory() == null ? null
                                                : SchoolArticleResponse.CategoryResponse.builder()
                                                                .categoryId(article.getCategory().getCategoryId())
                                                                .categoryName(article.getCategory().getCategoryName())
                                                                .build())
                                .vendors(vendors == null ? List.of()
                                                : vendors.stream()
                                                                .map(sav -> SchoolArticleResponse.VendorResponse
                                                                                .builder()
                                                                                .vendorId(sav.getVendor().getVendorId())
                                                                                .vendorName(sav.getVendor()
                                                                                                .getVendorName())
                                                                                .vendorInitial(sav.getVendor()
                                                                                                .getVendorInitial())
                                                                                .vendorType(sav.getVendor()
                                                                                                .getVendorType().name())
                                                                                .build())
                                                                .collect(Collectors.toList()))
                                .build();
        }

        String determineStatus(SchoolArticle article, LocalDate today) {
                if (article.getDueDate() != null && article.getDueDate().isBefore(today))
                        return "CLOSED";
                if (article.getStartDate() != null && article.getStartDate().isAfter(today))
                        return "UPCOMING";
                if (article.getDueDate() != null && !article.getDueDate().isAfter(today.plusDays(5)))
                        return "ENDING_SOON";
                return "OPEN";
        }

        private Map<Integer, Long> getBookmarkCountMap(List<Integer> articleIds) {
                return bookmarkRepository.countByArticleIdsAndArticleType(articleIds, VendorType.SCHOOL)
                                .stream()
                                .collect(Collectors.toMap(
                                                obj -> (Integer) obj[0],
                                                obj -> (Long) obj[1]));
        }

        // ===================== Admin Direct Maintenance Methods ===================== //

        /**
         * 관리자용 상세조회 (북마크 등 사용자 정보 제외)
         */
        @Transactional(readOnly = true)
        public AdminUnifiedDetailResponse getAdminArticleDetail(Integer articleId) {
                SchoolArticle article = schoolArticleRepository.findById(articleId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

                List<SchoolArticleVendor> vendors = schoolArticleVendorRepository.findAllByArticle(article);
                var attachments = attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL);

                return AdminUnifiedDetailResponse.builder()
                                .source("service")
                                .id(article.getArticleId())
                                .title(article.getTitle())
                                .content(article.getContent())
                                .startDate(article.getStartDate())
                                .dueDate(article.getDueDate())
                                .createdAt(article.getCreatedAt())
                                .updatedAt(article.getUpdatedAt())
                                .categories(article.getCategory() == null ? null
                                                : AdminUnifiedDetailResponse.CategoryResponse.builder()
                                                                .categoryId(article.getCategory().getCategoryId())
                                                                .categoryName(article.getCategory().getCategoryName())
                                                                .build())
                                .vendors(vendors.stream()
                                                .map(sav -> AdminUnifiedDetailResponse.VendorResponse.builder()
                                                                .vendorId(sav.getVendor().getVendorId())
                                                                .vendorName(sav.getVendor().getVendorName())
                                                                .vendorInitial(sav.getVendor().getVendorInitial())
                                                                .vendorType(sav.getVendor().getVendorType().name())
                                                                .originalUrl(sav.getOriginalUrl())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .attachments(attachments.stream()
                                                .map(att -> AdminUnifiedDetailResponse.AttachmentResponse.builder()
                                                                .fileId(att.getId())
                                                                .attachmentUrl(att.getAttachmentUrl())
                                                                .build())
                                                .collect(Collectors.toList()))
                                .build();
        }

        @Transactional(readOnly = true)
        public boolean checkArticleIdExists(Integer articleId) {
                return schoolArticleRepository.existsById(articleId);
        }

        @Transactional
        public Integer createArticleDirectly(AdminArticleCreateRequest request) {
                if (request.getArticleId() != null && schoolArticleRepository.existsById(request.getArticleId())) {
                        throw new BusinessException(ErrorCode.ALREADY_EXIST_ARTICLE);
                }

                Category category = null;
                if (request.getCategoryId() != null) {
                        category = categoryRepository.findById(request.getCategoryId())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
                }

                Integer finalArticleId = request.getArticleId();
                SchoolArticle article;

                if (finalArticleId != null) {
                        // 강제 삽입 (Native Query)
                        schoolArticleRepository.insertArticleDirectly(
                                finalArticleId,
                                request.getTitle(),
                                request.getContent(),
                                request.getCategoryId(),
                                request.getStartDate(),
                                request.getDueDate()
                        );
                        article = schoolArticleRepository.findById(finalArticleId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
                } else {
                        // 일반 삽입
                        article = SchoolArticle.builder()
                                .title(request.getTitle())
                                .content(request.getContent())
                                .startDate(request.getStartDate())
                                .dueDate(request.getDueDate())
                                .category(category)
                                .build();
                        article = schoolArticleRepository.save(article);
                        finalArticleId = article.getArticleId();
                }

                // 벤더 매핑 저장
                if (request.getVendors() != null) {
                        for (AdminArticleCreateRequest.VendorRequest vr : request.getVendors()) {
                                Vendor vendor = vendorRepository.findById(vr.getVendorId())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
                                SchoolArticleVendor articleVendor = SchoolArticleVendor.builder()
                                        .article(article)
                                        .vendor(vendor)
                                        .originalUrl(vr.getOriginalUrl())
                                        .build();
                                schoolArticleVendorRepository.save(articleVendor);
                        }
                }

                // 첨부파일 저장
                if (request.getAttachmentUrls() != null) {
                        for (String attUrl : request.getAttachmentUrls()) {
                                Attachment attachment = Attachment.builder()
                                        .articleId(finalArticleId)
                                        .articleType(VendorType.SCHOOL)
                                        .attachmentUrl(attUrl)
                                        .build();
                                attachmentRepository.save(attachment);
                        }
                }

                return finalArticleId;
        }

        @Transactional
        public void updateArticleDirectly(Integer articleId, AdminUnifiedUpdateRequest request) {
                SchoolArticle article = schoolArticleRepository.findById(articleId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

                Category category = null;
                if (request.getCategoryId() != null) {
                        category = categoryRepository.findById(request.getCategoryId())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
                }

                // null이 아닌 필드만 업데이트 (부분 수정 지원)
                LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : article.getStartDate();
                LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : article.getDueDate();
                Category finalCategory = category != null ? category : article.getCategory();

                article.update(
                        request.getTitle(),
                        request.getContent(),
                        startDate,
                        dueDate,
                        finalCategory
                );

                // 벤더 매핑 업데이트 (전체 삭제 후 재등록 방식, null이면 기존 유지)
                if (request.getVendors() != null) {
                        schoolArticleVendorRepository.deleteAllByArticle(article);
                        for (AdminUnifiedUpdateRequest.VendorRequest vr : request.getVendors()) {
                                Vendor vendor = vendorRepository.findById(vr.getVendorId())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
                                SchoolArticleVendor articleVendor = SchoolArticleVendor.builder()
                                        .article(article)
                                        .vendor(vendor)
                                        .originalUrl(vr.getOriginalUrl())
                                        .build();
                                schoolArticleVendorRepository.save(articleVendor);
                        }
                }

                // 첨부파일 업데이트 (전체 삭제 후 재등록 방식, null이면 기존 유지)
                if (request.getAttachmentUrls() != null) {
                        attachmentRepository.deleteAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL);
                        for (String attUrl : request.getAttachmentUrls()) {
                                Attachment attachment = Attachment.builder()
                                        .articleId(articleId)
                                        .articleType(VendorType.SCHOOL)
                                        .attachmentUrl(attUrl)
                                        .build();
                                attachmentRepository.save(attachment);
                        }
                }
        }
}

