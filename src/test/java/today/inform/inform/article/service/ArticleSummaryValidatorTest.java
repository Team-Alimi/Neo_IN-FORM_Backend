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
    @DisplayName("평서체·해요체·혼합 말투는 저장하지 않고 재생성 대상으로 분류한다")
    void rejectsNonFormalOrMixedEndings() {
        for (String ending : new String[] {"게시되었다.", "내용이다.", "진행한다.", "신청해요.", "아니다."}) {
            String output = valid.replace("신청은 온라인으로 진행합니다.", "신청은 온라인으로 " + ending);
            assertThatThrownBy(() -> validator.validate(output, source))
                    .isInstanceOfSatisfying(ArticleSummaryClient.Failure.class,
                            failure -> assertThat(failure.isRetryable()).isTrue());
        }
    }

    @Test
    @DisplayName("습니다·ㅂ니다 형태의 경어체 세 문장을 허용한다")
    void acceptsFormalPoliteEndings() {
        String output = """
                {"lines":["신청할 수 있습니다.","신청은 온라인으로 진행됩니다.","신청 내용을 확인하시기 바랍니다."]}
                """;
        assertThat(validator.validate(output, source)).isEqualTo(
                "신청할 수 있습니다.\n신청은 온라인으로 진행됩니다.\n신청 내용을 확인하시기 바랍니다.");
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
