package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleVendorRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("로그인 사용자는 전체 본문을 볼 수 있다.")
    void getSchoolArticleDetail_Authenticated() {
        // given
        Integer userId = 1;
        Integer articleId = 100;
        String fullContent = "A".repeat(200); // 200자 본문

        SchoolArticle article = SchoolArticle.builder()
                .articleId(articleId)
                .title("Test Title")
                .content(fullContent)
                .build();

        User user = User.builder().userId(userId).build();

        when(schoolArticleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bookmarkRepository.existsByUserAndArticleTypeAndArticleId(user, VendorType.SCHOOL, articleId)).thenReturn(true);
        when(schoolArticleVendorRepository.findAllByArticle(article)).thenReturn(List.of());
        when(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL)).thenReturn(List.of());

        // when
        SchoolArticleDetailResponse response = schoolArticleService.getSchoolArticleDetail(articleId, userId);

        // then
        assertThat(response.getContent()).isEqualTo(fullContent); // 원본 그대로
        assertThat(response.getIsBookmarked()).isTrue();
        
        // verify: User 조회가 일어났는지 확인
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("비로그인 사용자는 본문이 마스킹 처리된다.")
    void getSchoolArticleDetail_Anonymous() {
        // given
        Integer userId = null; // 비로그인
        Integer articleId = 100;
        String fullContent = "A".repeat(200); // 200자 본문

        SchoolArticle article = SchoolArticle.builder()
                .articleId(articleId)
                .title("Test Title")
                .content(fullContent)
                .build();

        when(schoolArticleRepository.findById(articleId)).thenReturn(Optional.of(article));
        // User, Bookmark 조회는 mocking하지 않음 (호출되지 않아야 하므로)
        when(schoolArticleVendorRepository.findAllByArticle(article)).thenReturn(List.of());
        when(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL)).thenReturn(List.of());

        // when
        SchoolArticleDetailResponse response = schoolArticleService.getSchoolArticleDetail(articleId, userId);

        // then
        assertThat(response.getContent()).isNotEqualTo(fullContent);
        assertThat(response.getContent()).contains("... (로그인 후 전체 내용을 확인하실 수 있습니다.)");
        assertThat(response.getContent().length()).isLessThan(fullContent.length());
        assertThat(response.getIsBookmarked()).isFalse();

        // verify: User 조회나 Bookmark 확인 로직이 실행되지 않았는지 검증 (성능/보안)
        verify(userRepository, never()).findById(any());
        verify(bookmarkRepository, never()).existsByUserAndArticleTypeAndArticleId(any(), any(), any());
    }
}
