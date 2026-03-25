package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.dto.SchoolArticleResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.SchoolArticleVendor;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.entity.Category;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.Attachment;
import today.inform.inform_backend.repository.*;
import today.inform.inform_backend.dto.AdminArticleCreateRequest;
import today.inform.inform_backend.dto.AdminUnifiedUpdateRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolArticleServiceTest {

        @InjectMocks
        private SchoolArticleService schoolArticleService;

        @Mock
        private SchoolArticleRepository schoolArticleRepository;
        @Mock
        private SchoolArticleVendorRepository schoolArticleVendorRepository;
        @Mock
        private AttachmentRepository attachmentRepository;
        @Mock
        private BookmarkRepository bookmarkRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private VendorRepository vendorRepository;

        @Test
        @DisplayName("인기 게시물 상위 10개를 조회한다.")
        void getHotSchoolArticles_Success() {
                // given
                Integer userId = 1;
                SchoolArticle article = SchoolArticle.builder()
                                .articleId(1)
                                .title("Hot Article")
                                .build();
                List<SchoolArticle> articles = List.of(article);
                User user = User.builder().userId(userId).build();

                when(schoolArticleRepository.findHotArticles(any(LocalDate.class), eq(10))).thenReturn(articles);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(bookmarkRepository.findAllByUserAndArticleTypeAndArticleIdIn(eq(user), eq(VendorType.SCHOOL),
                                anyList()))
                                .thenReturn(List.of());

                Object[] countResult = new Object[] { 1, 5L };
                when(bookmarkRepository.countByArticleIdsAndArticleType(anyList(), eq(VendorType.SCHOOL)))
                                .thenReturn(java.util.Collections.singletonList(countResult));

                // when
                List<SchoolArticleResponse> result = schoolArticleService.getHotSchoolArticles(userId);

                // then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getTitle()).isEqualTo("Hot Article");
                assertThat(result.get(0).getBookmarkCount()).isEqualTo(5L);
        }

        @Test
        @DisplayName("로그인 사용자는 전체 본문을 볼 수 있다.")
        void getSchoolArticleDetail_Authenticated() {
                // given
                Integer userId = 1;
                Integer articleId = 100;
                String fullContent = "A".repeat(200);

                SchoolArticle article = SchoolArticle.builder()
                                .articleId(articleId)
                                .title("Test Title")
                                .content(fullContent)
                                .build();

                User user = User.builder().userId(userId).build();

                when(schoolArticleRepository.findById(articleId)).thenReturn(Optional.of(article));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(bookmarkRepository.existsByUserAndArticleTypeAndArticleId(user, VendorType.SCHOOL, articleId))
                                .thenReturn(true);
                when(schoolArticleVendorRepository.findAllByArticle(article)).thenReturn(List.of());
                when(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL))
                                .thenReturn(List.of());

                // when
                SchoolArticleDetailResponse response = schoolArticleService.getSchoolArticleDetail(articleId, userId);

                // then
                assertThat(response.getContent()).isEqualTo(fullContent);
                assertThat(response.getIsBookmarked()).isTrue();
        }

        @Test
        @DisplayName("비로그인 사용자도 본문 전체를 조회할 수 있으며 북마크 여부는 false로 반환된다.")
        void getSchoolArticleDetail_Anonymous_Success() {
                // given
                Integer userId = null;
                Integer articleId = 100;
                String content = "전체 본문 내용입니다.";

                SchoolArticle article = SchoolArticle.builder()
                                .articleId(articleId)
                                .title("Test Title")
                                .content(content)
                                .build();

                when(schoolArticleRepository.findById(articleId)).thenReturn(Optional.of(article));
                when(schoolArticleVendorRepository.findAllByArticle(article)).thenReturn(List.of());
                when(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL))
                                .thenReturn(List.of());
                when(bookmarkRepository.countByArticleIdAndArticleType(articleId, VendorType.SCHOOL)).thenReturn(10L);

                // when
                SchoolArticleDetailResponse response = schoolArticleService.getSchoolArticleDetail(articleId, userId);

                // then
                assertThat(response.getContent()).isEqualTo(content);
                assertThat(response.getIsBookmarked()).isFalse();
        }

        @Test
        @DisplayName("진행 상태(status)로 필터링하여 공지사항 목록을 조회한다.")
        void getSchoolArticles_WithStatusFilter() {
                // given
                String status = "ENDING_SOON";
                Page<SchoolArticle> page = new PageImpl<>(List.of());

                when(schoolArticleRepository.findAllWithFiltersAndSorting(any(), any(), any(), any(), any(), any(),
                                any(),
                                any(), any(), any()))
                                .thenReturn(page);

                // when
                schoolArticleService.getSchoolArticles(1, 10, null, null, null, null, null, status, null);

                // then
                verify(schoolArticleRepository, times(1))
                                .findAllWithFiltersAndSorting(any(), any(), any(), any(), any(), eq(status), any(),
                                                any(), any(), any());
        }

        @Test
        @DisplayName("제공처 ID 리스트로 필터링하여 공지사항 목록을 조회한다.")
        void getSchoolArticles_WithVendorFilter() {
                // given
                List<Integer> vendorIds = List.of(5, 12);
                Page<SchoolArticle> page = new PageImpl<>(List.of());

                when(schoolArticleRepository.findAllWithFiltersAndSorting(any(), eq(vendorIds), any(), any(), any(),
                                any(), any(), any(), any(), any()))
                                .thenReturn(page);

                // when
                schoolArticleService.getSchoolArticles(1, 10, null, vendorIds, null, null, null, null, null);

                // then
                verify(schoolArticleRepository, times(1))
                                .findAllWithFiltersAndSorting(any(), eq(vendorIds), any(), any(), any(), any(), any(),
                                                any(), any(), any());
        }

        @Test
        @DisplayName("기존 게시글 ID 존재 여부를 확인한다.")
        void checkArticleIdExists_Success() {
                // given
                Integer articleId = 100;
                when(schoolArticleRepository.existsById(articleId)).thenReturn(true);

                // when
                boolean exists = schoolArticleService.checkArticleIdExists(articleId);

                // then
                assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("ID를 지정하지 않고 게시글을 직접 생성한다.")
        void createArticleDirectly_WithoutId() {
                // given
                AdminArticleCreateRequest request = AdminArticleCreateRequest.builder()
                                .title("Direct Title")
                                .content("Direct Content")
                                .categoryId(1)
                                .startDate(LocalDate.now())
                                .vendors(List.of(new AdminArticleCreateRequest.VendorRequest(1, null)))
                                .build();

                Category category = Category.builder().categoryId(1).build();
                Vendor vendor = Vendor.builder().vendorId(1).build();
                SchoolArticle article = SchoolArticle.builder().articleId(101).build();

                when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
                when(vendorRepository.findById(1)).thenReturn(Optional.of(vendor));
                when(schoolArticleRepository.save(any(SchoolArticle.class))).thenReturn(article);

                // when
                Integer resultId = schoolArticleService.createArticleDirectly(request);

                // then
                assertThat(resultId).isEqualTo(101);
                verify(schoolArticleRepository, times(1)).save(any(SchoolArticle.class));
                verify(schoolArticleVendorRepository, times(1)).save(any(SchoolArticleVendor.class));
        }

        @Test
        @DisplayName("ID를 명시하여 게시글을 직접 생성한다.")
        void createArticleDirectly_WithId() {
                // given
                Integer articleId = 200;
                AdminArticleCreateRequest request = AdminArticleCreateRequest.builder()
                                .articleId(articleId)
                                .title("Direct With ID")
                                .content("Direct Content")
                                .categoryId(1)
                                .startDate(LocalDate.now())
                                .vendors(List.of(new AdminArticleCreateRequest.VendorRequest(1, null)))
                                .build();

                Category category = Category.builder().categoryId(1).build();
                Vendor vendor = Vendor.builder().vendorId(1).build();
                SchoolArticle article = SchoolArticle.builder().articleId(articleId).build();

                when(schoolArticleRepository.existsById(articleId)).thenReturn(false);
                when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
                when(vendorRepository.findById(1)).thenReturn(Optional.of(vendor));
                when(schoolArticleRepository.findById(articleId)).thenReturn(Optional.of(article));

                // when
                Integer resultId = schoolArticleService.createArticleDirectly(request);

                // then
                assertThat(resultId).isEqualTo(articleId);
                verify(schoolArticleRepository, times(1)).insertArticleDirectly(eq(articleId), any(), any(), eq(1), any(), any());
                verify(schoolArticleVendorRepository, times(1)).save(any(SchoolArticleVendor.class));
        }

        @Test
        @DisplayName("이미 존재하는 ID로 게시글 생성을 시도하면 예외를 던진다.")
        void createArticleDirectly_DuplicateId_ThrowsException() {
                // given
                Integer articleId = 200;
                AdminArticleCreateRequest request = AdminArticleCreateRequest.builder()
                                .articleId(articleId)
                                .build();

                when(schoolArticleRepository.existsById(articleId)).thenReturn(true);

                // when & then
                assertThatThrownBy(() -> schoolArticleService.createArticleDirectly(request))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_EXIST_ARTICLE);
        }

        @Test
        @DisplayName("기존 게시글을 직접 수정한다.")
        void updateArticleDirectly_Success() {
                // given
                Integer articleId = 300;
                AdminUnifiedUpdateRequest request = AdminUnifiedUpdateRequest.builder()
                                .title("Updated Title")
                                .categoryId(2)
                                .vendors(List.of(new AdminUnifiedUpdateRequest.VendorRequest(1, null)))
                                .attachmentUrls(List.of("http://file.com"))
                                .build();

                Category category = Category.builder().categoryId(2).build();
                Vendor vendor = Vendor.builder().vendorId(1).build();
                SchoolArticle article = SchoolArticle.builder().articleId(articleId).build();

                when(schoolArticleRepository.findById(articleId)).thenReturn(Optional.of(article));
                when(categoryRepository.findById(2)).thenReturn(Optional.of(category));
                when(vendorRepository.findById(1)).thenReturn(Optional.of(vendor));

                // when
                schoolArticleService.updateArticleDirectly(articleId, request);

                // then
                verify(schoolArticleVendorRepository, times(1)).deleteAllByArticle(article);
                verify(attachmentRepository, times(1)).deleteAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL);
                verify(schoolArticleVendorRepository, times(1)).save(any(SchoolArticleVendor.class));
                verify(attachmentRepository, times(1)).save(any(Attachment.class));
        }
}
