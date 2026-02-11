package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.dto.ClubArticleListResponse;
import today.inform.inform_backend.dto.SchoolArticleListResponse;
import today.inform.inform_backend.entity.Bookmark;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private UserRepository userRepository;
    @Mock private SchoolArticleService schoolArticleService;
    @Mock private ClubArticleService clubArticleService;

    @InjectMocks private BookmarkService bookmarkService;

    @Test
    @DisplayName("내가 북마크한 학교 공지사항 목록을 조회한다.")
    void getBookmarkedSchoolArticles_Success() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        Bookmark bookmark = Bookmark.builder().articleId(105).articleType(VendorType.SCHOOL).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(bookmarkRepository.findAllByUserAndArticleTypeOrderByCreatedAtDesc(user, VendorType.SCHOOL)).willReturn(List.of(bookmark));
        
        SchoolArticleListResponse expectedResponse = SchoolArticleListResponse.builder()
                .school_articles(List.of()) // 내용은 Service에서 채워짐
                .build();
        given(schoolArticleService.getSchoolArticlesByIds(any(), any(), any(), eq(userId))).willReturn(expectedResponse);

        // when
        SchoolArticleListResponse response = bookmarkService.getBookmarkedSchoolArticles(userId, 1, 10);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("북마크한 글이 없으면 빈 목록을 반환한다.")
    void getBookmarkedSchoolArticles_Empty() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(bookmarkRepository.findAllByUserAndArticleTypeOrderByCreatedAtDesc(user, VendorType.SCHOOL)).willReturn(List.of());

        // when
        SchoolArticleListResponse response = bookmarkService.getBookmarkedSchoolArticles(userId, 1, 10);

        // then
        assertThat(response.getSchool_articles()).isEmpty();
        assertThat(response.getPage_info().getTotal_articles()).isEqualTo(0L);
    }
}
