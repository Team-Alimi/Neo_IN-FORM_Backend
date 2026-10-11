package today.inform.inform.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArticleSummaryValidatorTest {
    private final ArticleSummaryValidator validator = new ArticleSummaryValidator();
    private final ArticleSummarySource source = new ArticleSummarySource(1L, "장학금 신청 안내",
            "재학생은 장학금을 신청할 수 있습니다. 신청은 온라인으로 진행합니다. 마감은 2026-10-15입니다.",
            null, LocalDate.of(2026, 10, 15), 0L, OffsetDateTime.parse("2026-10-11T00:00:00Z"));
    private final String valid = """
            {"lines":["재학생은 장학금을 신청할 수 있습니다.","신청은 온라인으로 진행합니다.","마감은 2026-10-15입니다."]}
            """;

    @Test
    @DisplayName("검증한 세 문장을 빈 줄 없이 저장한다")
    void joinsExactlyThreeSentences() {
        assertThat(validator.validate(valid, source)).isEqualTo(
                "재학생은 장학금을 신청할 수 있습니다.\n신청은 온라인으로 진행합니다.\n마감은 2026-10-15입니다.");
    }

    @Test
    @DisplayName("빈 줄·추가 문장·bullet·원문에 없는 날짜를 거부한다")
    void rejectsInvalidFormatAndInventedDates() {
        for (String invalid : new String[] {
                "{\"lines\":[\"첫 문장입니다.\",\"두 번째입니다.\"]}",
                "{\"lines\":[\"첫 문장입니다.\",\" \",\"세 번째입니다.\"]}",
                "{\"lines\":[\"첫 문장입니다. 다음 문장입니다.\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                "{\"lines\":[\"첫 문장입니다.다음 문장입니다.\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                "{\"lines\":[\"첫 문장입니다.\\u2028\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                "{\"lines\":[\"- 첫 문장입니다.\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                "{\"lines\":[\"첫 문장입니다.\\n\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                "{\"lines\":[\"마감은 2026-10-16입니다.\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                "{\"lines\":[\"마감은 10/16입니다.\",\"두 번째입니다.\",\"세 번째입니다.\"]}",
                valid + " 추가 설명",
                "{\"lines\":[\"첫 문장입니다.\",\"두 번째입니다.\",\"세 번째입니다.\"],\"reasoning\":\"hidden\"}",
                "not JSON" }) {
            assertThatThrownBy(() -> validator.validate(invalid, source))
                    .isInstanceOf(ArticleSummaryClient.Failure.class);
        }
    }
}
