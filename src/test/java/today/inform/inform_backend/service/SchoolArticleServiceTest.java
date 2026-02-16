package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.dto.SchoolArticleDetailResponse;
import today.inform.inform_backend.dto.SchoolArticleResponse;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(bookmarkRepository.findAllByUserAndArticleTypeAndArticleIdIn(eq(user), eq(VendorType.SCHOOL), anyList()))
                .thenReturn(List.of()); // 북마크 안한 상태
        
        Object[] countResult = new Object[]{1, 5L};
        when(bookmarkRepository.countByArticleIdsAndArticleType(anyList(), eq(VendorType.SCHOOL)))
                .thenReturn(java.util.Collections.singletonList(countResult)); // 북마크 수 5개

        // when
        List<SchoolArticleResponse> result = schoolArticleService.getHotSchoolArticles(userId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Hot Article");
        assertThat(result.get(0).getBookmarkCount()).isEqualTo(5L);
        verify(schoolArticleRepository, times(1)).findHotArticles(any(), eq(10));
    }

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
        when(attachmentRepository.findAllByArticleIdAndArticleType(articleId, VendorType.SCHOOL)).thenReturn(List.of());
        when(bookmarkRepository.countByArticleIdAndArticleType(articleId, VendorType.SCHOOL)).thenReturn(10L);

        // when
        SchoolArticleDetailResponse response = schoolArticleService.getSchoolArticleDetail(articleId, userId);

        // then
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getIsBookmarked()).isFalse();
        assertThat(response.getBookmarkCount()).isEqualTo(10L);
        
        // verify: 비로그인이므로 UserRepository를 조회하지 않아야 함
        verify(userRepository, never()).findById(any());
    }
}
