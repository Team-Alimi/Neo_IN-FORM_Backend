package today.inform.inform.article.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ArticleSummaryValidator {

    private static final Pattern SENTENCE_END = Pattern.compile("(?<!\\d)\\.|\\.(?!\\d)|[!?。]+");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final Pattern BULLET = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s*");
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(?:\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}"
                    + "|(?:\\d{4}년\\s*)?\\d{1,2}월\\s*\\d{1,2}일"
                    + "|\\d{1,2}/\\d{1,2})(?!\\d)");
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /** 형식·경어체 종결·명시적 날짜를 검증합니다. 일반적인 사실성 전체의 자동 판정은 아닙니다. */
    public String validate(String content, ArticleSummarySource source) {
        try {
            JsonNode root = mapper.readTree(content);
            if (root == null || !root.isObject() || root.size() != 1
                    || !root.has("lines") || !root.get("lines").isArray()
                    || root.get("lines").size() != 3) {
                throw new ArticleSummaryClient.Failure(true);
            }
            Set<String> sourceDates = dates(source.title() + " " + source.content());
            if (source.startsOn() != null) {
                addDate(sourceDates, source.startsOn().toString());
            }
            if (source.endsOn() != null) {
                addDate(sourceDates, source.endsOn().toString());
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode node : root.get("lines")) {
                if (!node.isString()) {
                    throw new ArticleSummaryClient.Failure(true);
                }
                String original = node.asString();
                String line = original.strip();
                Matcher endings = SENTENCE_END.matcher(line);
                int count = 0;
                while (endings.find()) {
                    count++;
                }
                if (line.isBlank() || LINE_BREAK.matcher(original).find()
                        || BULLET.matcher(line).find() || count != 1
                        || !line.matches("(?s).*[.!?。]$")
                        || !endsWithFormalPoliteEnding(line)
                        || !sourceDates.containsAll(dates(line))) {
                    throw new ArticleSummaryClient.Failure(true);
                }
                lines.add(line);
            }
            return String.join("\n", lines);
        } catch (ArticleSummaryClient.Failure e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArticleSummaryClient.Failure(true);
        }
    }

    private static boolean endsWithFormalPoliteEnding(String line) {
        if (line.endsWith("습니다.")) {
            return true;
        }
        if (!line.endsWith("니다.") || line.length() < 4) {
            return false;
        }
        char syllable = line.charAt(line.length() - 4);
        // 한글 완성형의 종성은 28개이며 ㅂ 은 index 17 입니다. '아니다.' 와 구분합니다.
        return syllable >= '가' && syllable <= '힣' && (syllable - '가') % 28 == 17;
    }

    private static Set<String> dates(String text) {
        Set<String> dates = new HashSet<>();
        Matcher matcher = DATE.matcher(text);
        while (matcher.find()) {
            addDate(dates, matcher.group());
        }
        return dates;
    }

    private static void addDate(Set<String> dates, String text) {
        Matcher numbers = Pattern.compile("\\d+").matcher(text);
        List<Integer> parts = new ArrayList<>();
        while (numbers.find()) {
            parts.add(Integer.parseInt(numbers.group()));
        }
        dates.add(parts.toString());
        if (parts.size() == 3) {
            dates.add(parts.subList(1, 3).toString());
        }
    }
}
