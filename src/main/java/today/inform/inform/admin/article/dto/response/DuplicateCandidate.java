package today.inform.inform.admin.article.dto.response;

import java.util.List;
import today.inform.inform.article.entity.ArticleStatus;

/**
 * ADM-12 중복 확인 결과 한 건 (명세 4.8).
 *
 * <p>응답 전체는 {@code { "exists": true, "articles": [ ... ] }} 형태입니다.
 * {@code exists} 를 따로 두는 이유는 화면이 목록 길이를 세지 않고 바로 분기하기 위해서입니다.
 */
public record DuplicateCandidate(Long id, String title, ArticleStatus status) {

    /** 응답 봉투. */
    public record Result(boolean exists, List<DuplicateCandidate> articles) {

        public static Result of(List<DuplicateCandidate> found) {
            return new Result(!found.isEmpty(), found);
        }
    }
}
