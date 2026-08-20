package today.inform.inform.like.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.repository.ArticleReactionRepository;
import today.inform.inform.article.repository.ReactionType;
import today.inform.inform.article.service.ArticleReadableChecker;

/**
 * LIK-01 좋아요 토글. 계정당 1표입니다.
 *
 * <p>1표 제한은 앱이 아니라 <b>복합 PK 가 강제</b>합니다.
 * 앱에서 "이미 눌렀는지 확인 후 INSERT" 로 짜면 연타 시 두 요청이 모두 확인을 통과해
 * 한쪽이 PK 위반으로 500 이 됩니다. {@code ON CONFLICT DO NOTHING} 이면 그 경합이 없습니다.
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final ArticleReactionRepository reactionRepository;
    private final ArticleReadableChecker readableChecker;

    /** 멱등. 이미 눌렀으면 아무 일도 없습니다. */
    @Transactional
    public void like(Long userId, Long articleId) {
        readableChecker.requireReadable(articleId);
        reactionRepository.add(ReactionType.LIKE, userId, articleId);
    }

    /** 멱등. 누르지 않았어도 오류가 아닙니다. */
    @Transactional
    public void unlike(Long userId, Long articleId) {
        reactionRepository.remove(ReactionType.LIKE, userId, articleId);
    }
}
