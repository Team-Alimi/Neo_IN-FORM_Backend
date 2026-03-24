package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.entity.SchoolArticleSandbox;
import today.inform.inform_backend.entity.SchoolArticleVendorSandbox;
import today.inform.inform_backend.entity.AttachmentSandbox;
import today.inform.inform_backend.entity.Category;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.entity.Attachment;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.dto.SandboxArticleUpdateRequest;

import today.inform.inform_backend.repository.SchoolArticleSandboxRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorSandboxRepository;
import today.inform.inform_backend.repository.AttachmentSandboxRepository;
import today.inform.inform_backend.repository.CategoryRepository;
import today.inform.inform_backend.repository.VendorRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.repository.AttachmentRepository;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class SchoolArticleSandboxService {

    private final SchoolArticleSandboxRepository sandboxRepository;
    private final SchoolArticleVendorSandboxRepository vendorSandboxRepository;
    private final AttachmentSandboxRepository attachmentSandboxRepository;
    private final CategoryRepository categoryRepository;
    private final VendorRepository vendorRepository;

    // 실서비스 운영 테이블 Repository
    private final SchoolArticleRepository schoolArticleRepository;
    private final SchoolArticleVendorRepository schoolArticleVendorRepository;
    private final AttachmentRepository attachmentRepository;

    /**
     * 크롤러가 수집한 데이터를 샌드박스에 저장
     */
    @Transactional
    public Integer createSandboxArticle(String title, String content, Integer categoryId, 
                                      List<SandboxArticleUpdateRequest.VendorRequest> vendors, 
                                      List<String> attachmentUrls) {
        Category category = null;
        if (categoryId != null) {
            category = categoryRepository.findById(categoryId).orElse(null);
        }

        SchoolArticleSandbox sandbox = SchoolArticleSandbox.builder()
                .title(title)
                .content(content)
                .category(category)
                .adminStatus(AdminStatus.INSPECTED_YET)
                .build();

        sandbox = sandboxRepository.save(sandbox);

        // 벤더 정보 저장
        if (vendors != null) {
            for (SandboxArticleUpdateRequest.VendorRequest vr : vendors) {
                Vendor vendor = vendorRepository.findById(vr.getVendorId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

                SchoolArticleVendorSandbox vendorSandbox = SchoolArticleVendorSandbox.builder()
                        .sandboxArticle(sandbox)
                        .vendor(vendor)
                        .originalUrl(vr.getOriginalUrl())
                        .build();
                vendorSandboxRepository.save(vendorSandbox);
            }
        }

        // 첨부파일 저장
        if (attachmentUrls != null) {
            for (String url : attachmentUrls) {
                AttachmentSandbox attachmentSandbox = AttachmentSandbox.builder()
                        .sandboxArticle(sandbox)
                        .attachmentUrl(url)
                        .build();
                attachmentSandboxRepository.save(attachmentSandbox);
            }
        }

        return sandbox.getSandboxId();
    }

    /**
     * 상태별 샌드박스 게시글 목록 조회 (페이징 지원)
     */
    @Transactional(readOnly = true)
    public Page<SchoolArticleSandbox> getArticlesByStatus(AdminStatus status, Pageable pageable) {
        return sandboxRepository.findAllByAdminStatusOrderByCreatedAtAsc(status, pageable);
    }

    /**
     * 샌드박스 게시글 상세 조회
     */
    @Transactional(readOnly = true)
    public SchoolArticleSandbox getArticleDetail(Integer sandboxId) {
        return sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    /**
     * 연관된 벤더 정보 조회
     */
    @Transactional(readOnly = true)
    public List<SchoolArticleVendorSandbox> getVendors(Integer sandboxId) {
        return vendorSandboxRepository.findAllBySandboxArticleSandboxId(sandboxId);
    }

    /**
     * 연관된 첨부파일 정보 조회
     */
    @Transactional(readOnly = true)
    public List<AttachmentSandbox> getAttachments(Integer sandboxId) {
        return attachmentSandboxRepository.findAllBySandboxArticleSandboxId(sandboxId);
    }

    /**
     * 관리자 검수 및 수정 (메인 정보 + 제공처/첨부파일 목록 전체 업데이트)
     */
    @Transactional
    public void updateArticle(Integer sandboxId, SandboxArticleUpdateRequest request) {
        SchoolArticleSandbox sandbox = sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        // 1. 메인 정보 업데이트 (null이 아닌 필드만 업데이트)
        AdminStatus status = null;
        if (request.getAdminStatus() != null) {
            try {
                status = AdminStatus.valueOf(request.getAdminStatus());
            } catch (IllegalArgumentException e) {
                // 무시하거나 예외 처리
            }
        }

        String title = request.getTitle() != null ? request.getTitle() : sandbox.getTitle();
        String content = request.getContent() != null ? request.getContent() : sandbox.getContent();
        Category finalCategory = category != null ? category : sandbox.getCategory();
        AdminStatus finalStatus = status != null ? status : sandbox.getAdminStatus();
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : sandbox.getStartDate();
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : sandbox.getDueDate();

        sandbox.update(title, content, finalCategory, finalStatus, startDate, dueDate);

        // 2. 벤더 정보 업데이트 (전체 삭제 후 재등록 방식)
        if (request.getVendors() != null) {
            vendorSandboxRepository.deleteAllBySandboxArticleSandboxId(sandboxId);
            for (SandboxArticleUpdateRequest.VendorRequest vr : request.getVendors()) {
                Vendor vendor = vendorRepository.findById(vr.getVendorId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

                SchoolArticleVendorSandbox vendorSandbox = SchoolArticleVendorSandbox.builder()
                        .sandboxArticle(sandbox)
                        .vendor(vendor)
                        .originalUrl(vr.getOriginalUrl())
                        .build();
                vendorSandboxRepository.save(vendorSandbox);
            }
        }

        // 3. 첨부파일 업데이트 (전체 삭제 후 재등록 방식)
        if (request.getAttachmentUrls() != null) {
            attachmentSandboxRepository.deleteAllBySandboxArticleSandboxId(sandboxId);
            for (String url : request.getAttachmentUrls()) {
                AttachmentSandbox attachmentSandbox = AttachmentSandbox.builder()
                        .sandboxArticle(sandbox)
                        .attachmentUrl(url)
                        .build();
                attachmentSandboxRepository.save(attachmentSandbox);
            }
        }
    }

    /**
     * 상태만 변경 (GARBAGE로 변경 시 이전 상태 자동 보존)
     */
    @Transactional
    public void updateStatuses(List<Integer> sandboxIds, AdminStatus status) {
        List<SchoolArticleSandbox> sandboxes = sandboxRepository.findAllById(sandboxIds);
        if (sandboxes.isEmpty()) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        if (status == AdminStatus.GARBAGE) {
            sandboxes.forEach(SchoolArticleSandbox::moveToGarbage);
        } else {
            sandboxes.forEach(sandbox -> sandbox.updateStatus(status));
        }
    }

    /**
     * 휴지통 게시글 복구 (이전 상태로 되돌림)
     */
    @Transactional
    public void restoreArticles(List<Integer> sandboxIds) {
        List<SchoolArticleSandbox> sandboxes = sandboxRepository.findAllById(sandboxIds);
        if (sandboxes.isEmpty()) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        for (SchoolArticleSandbox sandbox : sandboxes) {
            if (sandbox.getAdminStatus() != AdminStatus.GARBAGE) {
                throw new BusinessException(ErrorCode.NOT_IN_GARBAGE);
            }
        }

        sandboxes.forEach(SchoolArticleSandbox::restore);
    }

    /**
     * 샌드박스 게시글 삭제 (영구 삭제)
     */
    @Transactional
    public void deleteArticles(List<Integer> sandboxIds) {
        for (Integer id : sandboxIds) {
            deleteSingleArticle(id);
        }
    }

    private void deleteSingleArticle(Integer sandboxId) {
        vendorSandboxRepository.deleteAllBySandboxArticleSandboxId(sandboxId);
        attachmentSandboxRepository.deleteAllBySandboxArticleSandboxId(sandboxId);
        sandboxRepository.deleteById(sandboxId);
    }

    /**
     * [핵심] 샌드박스 -> 실서비스 데이터 배포(Deploy)
     */
    @Transactional
    public List<Integer> deployArticles(List<Integer> sandboxIds) {
        List<Integer> newArticleIds = new java.util.ArrayList<>();
        for (Integer id : sandboxIds) {
            newArticleIds.add(deploySingleArticle(id));
        }
        return newArticleIds;
    }

    private Integer deploySingleArticle(Integer sandboxId) {
        // 1. 샌드박스 데이터 조회 (연관 데이터 포함)
        SchoolArticleSandbox sandbox = sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        List<SchoolArticleVendorSandbox> vendorSandboxes = vendorSandboxRepository.findAllBySandboxArticleSandboxId(sandboxId);
        List<AttachmentSandbox> attachmentSandboxes = attachmentSandboxRepository.findAllBySandboxArticleSandboxId(sandboxId);

        // 2. 실서비스 게시글 생성
        SchoolArticle article = SchoolArticle.builder()
                .title(sandbox.getTitle())
                .content(sandbox.getContent())
                .startDate(sandbox.getStartDate())
                .dueDate(sandbox.getDueDate())
                .category(sandbox.getCategory())
                .build();
        
        article = schoolArticleRepository.save(article);
        Integer newArticleId = article.getArticleId();

        // 3. 실서비스 제공처 매핑 저장
        for (SchoolArticleVendorSandbox vs : vendorSandboxes) {
            SchoolArticleVendor productionVendor = SchoolArticleVendor.builder()
                    .article(article)
                    .vendor(vs.getVendor())
                    .originalUrl(vs.getOriginalUrl())
                    .build();
            schoolArticleVendorRepository.save(productionVendor);
        }

        // 4. 실서비스 첨부파일 저장
        for (AttachmentSandbox as : attachmentSandboxes) {
            Attachment productionAttachment = Attachment.builder()
                    .attachmentUrl(as.getAttachmentUrl())
                    .articleId(newArticleId)
                    .articleType(VendorType.SCHOOL)
                    .build();
            attachmentRepository.save(productionAttachment);
        }

        // 5. 샌드박스 데이터 삭제 (청소)
        deleteSingleArticle(sandboxId);

        return newArticleId;
    }

    /**
     * 상태별 게시글 통계 (개수) 조회
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getSandboxCounts() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        counts.put("inspected_yet", sandboxRepository.countByAdminStatus(AdminStatus.INSPECTED_YET));
        counts.put("reflection_waiting", sandboxRepository.countByAdminStatus(AdminStatus.REFLECTION_WAITING));
        counts.put("suspected_duplicate", sandboxRepository.countByAdminStatus(AdminStatus.SUSPECTED_DUPLICATE));
        counts.put("garbage", sandboxRepository.countByAdminStatus(AdminStatus.GARBAGE));
        return counts;
    }
}
