package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.entity.*;
import today.inform.inform_backend.repository.*;

import today.inform.inform_backend.dto.SandboxArticleUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolArticleSandboxServiceTest {

    @InjectMocks
    private SchoolArticleSandboxService sandboxService;

    @Mock private SchoolArticleSandboxRepository sandboxRepository;
    @Mock private SchoolArticleVendorSandboxRepository vendorSandboxRepository;
    @Mock private AttachmentSandboxRepository attachmentSandboxRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private VendorRepository vendorRepository;
    @Mock private SchoolArticleRepository schoolArticleRepository;
    @Mock private SchoolArticleVendorRepository schoolArticleVendorRepository;
    @Mock private AttachmentRepository attachmentRepository;

    @Test
    @DisplayName("샌드박스 게시글 배포 테스트 - 운영 테이블로 정상 이동되는지 확인")
    void deployArticleTest() {
        // given
        Integer sandboxId = 1;
        Category category = Category.builder().categoryId(1).categoryName("학사공지").build();
        Vendor vendor = Vendor.builder().vendorId(1).vendorName("서울대학교").build();
        
        SchoolArticleSandbox sandbox = SchoolArticleSandbox.builder()
                .sandboxId(sandboxId)
                .title("테스트 제목")
                .content("테스트 내용")
                .category(category)
                .adminStatus(AdminStatus.REFLECTION_WAITING)
                .build();

        SchoolArticleVendorSandbox vs = SchoolArticleVendorSandbox.builder()
                .vendor(vendor)
                .originalUrl("https://original.url")
                .build();

        AttachmentSandbox as = AttachmentSandbox.builder()
                .attachmentUrl("https://s3.url/file.pdf")
                .build();

        given(sandboxRepository.findById(sandboxId)).willReturn(Optional.of(sandbox));
        given(vendorSandboxRepository.findAllBySandboxArticleSandboxId(sandboxId)).willReturn(List.of(vs));
        given(attachmentSandboxRepository.findAllBySandboxArticleSandboxId(sandboxId)).willReturn(List.of(as));
        
        SchoolArticle savedArticle = SchoolArticle.builder().articleId(100).build();
        given(schoolArticleRepository.save(any(SchoolArticle.class))).willReturn(savedArticle);

        // when
        List<Integer> newArticleIds = sandboxService.deployArticles(List.of(sandboxId));

        // then
        assertThat(newArticleIds).hasSize(1);
        assertThat(newArticleIds.get(0)).isEqualTo(100);
        
        // 운영 테이블 저장 확인
        verify(schoolArticleRepository, times(1)).save(any(SchoolArticle.class));
        verify(schoolArticleVendorRepository, times(1)).save(any(SchoolArticleVendor.class));
        verify(attachmentRepository, times(1)).save(any(Attachment.class));
        
        // 샌드박스 데이터 삭제 확인
        verify(sandboxRepository, times(1)).deleteById(sandboxId);
        verify(vendorSandboxRepository, times(1)).deleteAllBySandboxArticleSandboxId(sandboxId);
        verify(attachmentSandboxRepository, times(1)).deleteAllBySandboxArticleSandboxId(sandboxId);
    }

    @Test
    @DisplayName("상태 통계 조회 테스트")
    void getSandboxCountsTest() {
        // given
        given(sandboxRepository.countByAdminStatus(AdminStatus.INSPECTED_YET)).willReturn(5L);
        given(sandboxRepository.countByAdminStatus(AdminStatus.REFLECTION_WAITING)).willReturn(3L);
        given(sandboxRepository.countByAdminStatus(AdminStatus.SUSPECTED_DUPLICATE)).willReturn(1L);
        given(sandboxRepository.countByAdminStatus(AdminStatus.GARBAGE)).willReturn(10L);

        // when
        java.util.Map<String, Long> counts = sandboxService.getSandboxCounts();

        // then
        assertThat(counts.get("inspected_yet")).isEqualTo(5L);
        assertThat(counts.get("reflection_waiting")).isEqualTo(3L);
        assertThat(counts.get("suspected_duplicate")).isEqualTo(1L);
        assertThat(counts.get("garbage")).isEqualTo(10L);
    }

    @Test
    @DisplayName("샌드박스 게시글 상세 수정 테스트")
    void updateArticleTest() {
        // given
        Integer sandboxId = 1;
        SandboxArticleUpdateRequest request = SandboxArticleUpdateRequest.builder()
                .title("수정된 제목")
                .content("수정된 내용")
                .categoryId(1)
                .adminStatus("GARBAGE")
                .vendorIds(List.of(1))
                .originalUrls(List.of("https://new-url.com"))
                .attachmentUrls(List.of("https://s3.url/new-file.pdf"))
                .build();

        SchoolArticleSandbox sandbox = SchoolArticleSandbox.builder()
                .sandboxId(sandboxId)
                .build();
        
        Category category = Category.builder().categoryId(1).build();
        Vendor vendor = Vendor.builder().vendorId(1).build();

        given(sandboxRepository.findById(sandboxId)).willReturn(Optional.of(sandbox));
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));
        given(vendorRepository.findById(1)).willReturn(Optional.of(vendor));

        // when
        sandboxService.updateArticle(sandboxId, request);

        // then
        verify(sandboxRepository, times(1)).findById(sandboxId);
        verify(vendorSandboxRepository, times(1)).deleteAllBySandboxArticleSandboxId(sandboxId);
        verify(vendorSandboxRepository, times(1)).save(any(SchoolArticleVendorSandbox.class));
        verify(attachmentSandboxRepository, times(1)).deleteAllBySandboxArticleSandboxId(sandboxId);
        verify(attachmentSandboxRepository, times(1)).save(any(AttachmentSandbox.class));
    }
}
