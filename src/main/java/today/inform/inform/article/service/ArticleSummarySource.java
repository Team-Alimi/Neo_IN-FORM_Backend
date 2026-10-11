package today.inform.inform.article.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import today.inform.inform.article.entity.Article;

/** AI 호출 전에 한 번 읽은 원문과 조건부 저장 기준. */
public record ArticleSummarySource(Long id, String title, String content,
                                   LocalDate startsOn, LocalDate endsOn,
                                   Long version, OffsetDateTime updatedAt) {

    public static ArticleSummarySource from(Article article) {
        return new ArticleSummarySource(article.getId(), article.getTitle(), article.getContent(),
                article.getStartsOn(), article.getEndsOn(), article.getVersion(), article.getUpdatedAt());
    }
}
