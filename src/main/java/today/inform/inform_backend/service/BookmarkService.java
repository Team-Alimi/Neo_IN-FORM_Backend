package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.entity.Bookmark;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.ClubArticleRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final SchoolArticleRepository schoolArticleRepository;
    private final ClubArticleRepository clubArticleRepository;
    private final SchoolArticleService schoolArticleService;
    private final ClubArticleService clubArticleService;

    @Transactional(readOnly = true)
    public today.inform.inform_backend.dto.SchoolArticleListResponse getBookmarkedSchoolArticles(Integer userId, List<Integer> categoryIds, String keyword, Integer page, Integer size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Integer> articleIds = bookmarkRepository.findAllByUserAndArticleTypeOrderByCreatedAtDesc(user, VendorType.SCHOOL)
                .stream()
                .map(Bookmark::getArticleId)
                .collect(java.util.stream.Collectors.toList());

        if (articleIds.isEmpty()) {
            return today.inform.inform_backend.dto.SchoolArticleListResponse.builder()
                    .pageInfo(today.inform.inform_backend.dto.SchoolArticleListResponse.PageInfo.builder()
                            .currentPage(page)
                            .totalPages(0)
                            .totalArticles(0L)
                            .hasNext(false)
                            .build())
                    .schoolArticles(List.of())
                    .build();
        }

        return schoolArticleService.getSchoolArticlesByIds(articleIds, categoryIds, keyword, page, size, userId);
    }

    @Transactional
    public void deleteAllBookmarkedSchoolArticles(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        bookmarkRepository.deleteAllByUserAndArticleType(user, VendorType.SCHOOL);
    }

    @Transactional
    public boolean toggleBookmark(Integer userId, VendorType articleType, Integer articleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 보안: 학교 공지만 북마크 허용
        if (articleType != VendorType.SCHOOL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "학교 공지만 스크랩할 수 있습니다.");
        }

        // 1. 게시글 존재 여부 확인
        validateArticleExists(articleType, articleId);

        // 2. 이미 북마크가 있는지 확인
        Optional<Bookmark> existingBookmark = bookmarkRepository.findByUserAndArticleTypeAndArticleId(user, articleType, articleId);

        if (existingBookmark.isPresent()) {
            // 있으면 삭제 (해제)
            bookmarkRepository.delete(existingBookmark.get());
            return false;
        } else {
            // 없으면 등록
            Bookmark bookmark = Bookmark.builder()
                    .user(user)
                    .articleType(articleType)
                    .articleId(articleId)
                    .build();
            bookmarkRepository.save(bookmark);
            return true;
        }
    }

    private void validateArticleExists(VendorType articleType, Integer articleId) {
        if (articleType == VendorType.SCHOOL) {
            if (!schoolArticleRepository.existsById(articleId)) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }
        }
    }
}
