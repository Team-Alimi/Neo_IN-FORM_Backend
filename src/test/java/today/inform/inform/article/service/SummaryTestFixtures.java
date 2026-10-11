package today.inform.inform.article.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import today.inform.inform.article.config.ArticleSummaryProperties;

final class SummaryTestFixtures {
    static final String CONTENT = """
            {"lines":["재학생은 장학금을 신청할 수 있습니다.","신청은 온라인으로 진행합니다.","접수 내용은 공지를 확인해야 합니다."]}
            """;
    static final ArticleSummarySource SOURCE = new ArticleSummarySource(1L, "장학금 신청 안내",
            "재학생은 장학금을 신청할 수 있습니다. 신청은 온라인으로 진행합니다. 접수 내용은 공지를 확인해야 합니다.",
            null, null, 3L, OffsetDateTime.parse("2026-10-11T00:00:00Z"));

    static ArticleSummaryProperties properties() {
        return new ArticleSummaryProperties(Duration.ofSeconds(20), 2, 20, 2,
                Duration.ofSeconds(60), Duration.ofSeconds(90), Duration.ofSeconds(70),
                100, 250000, 512);
    }

    private SummaryTestFixtures() {
    }
}
