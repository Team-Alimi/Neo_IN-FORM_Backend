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
        given(schoolArticleService.getSchoolArticlesByIds(any(), any(), any(), any(), any(), eq(userId))).willReturn(expectedResponse);

        // when
        SchoolArticleListResponse response = bookmarkService.getBookmarkedSchoolArticles(userId, null, null, 1, 10);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("내가 북마크한 모든 학교 공지사항을 삭제한다.")
    void deleteAllBookmarkedSchoolArticles_Success() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        bookmarkService.deleteAllBookmarkedSchoolArticles(userId);

        // then
        org.mockito.Mockito.verify(bookmarkRepository, org.mockito.Mockito.times(1))
                .deleteAllByUserAndArticleType(user, VendorType.SCHOOL);
    }

    @Test
    @DisplayName("학교 공지가 아닌 글을 북마크하려고 하면 예외가 발생한다.")
    void toggleBookmark_NotSchool_Fail() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookmarkService.toggleBookmark(userId, VendorType.CLUB, 100))
                .isInstanceOf(today.inform.inform_backend.common.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(today.inform.inform_backend.common.exception.ErrorCode.INVALID_INPUT_VALUE);
    }
}
