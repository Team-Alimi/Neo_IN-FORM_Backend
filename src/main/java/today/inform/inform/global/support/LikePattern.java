package today.inform.inform.global.support;

/**
 * 사용자가 입력한 검색어를 LIKE 패턴으로 안전하게 바꾼다.
 *
 * <p>그대로 넘기면 입력한 글자가 <b>패턴 문법으로 해석된다.</b>
 * {@code "100%"} 를 찾으면 {@code "100"} 으로 시작하는 것이 전부 걸리고,
 * {@code "%"} 하나면 전체가 걸린다. 제목이나 이메일에 {@code %} 나 {@code _} 가
 * 들어가는 경우는 드물지 않다.
 *
 * <p><b>쿼리에 {@code ESCAPE '\'} 를 반드시 함께 적어야 한다.</b>
 * PostgreSQL 의 기본 이스케이프 문자가 역슬래시이긴 하지만,
 * {@code standard_conforming_strings} 설정에 따라 해석이 달라질 수 있어 명시하는 편이 안전하다.
 * 여기서 만든 패턴을 {@code ESCAPE} 없이 쓰면 이스케이프가 무의미해진다.
 */
public final class LikePattern {

    private LikePattern() {
    }

    /** {@code %keyword%} — 부분 일치. */
    public static String contains(String keyword) {
        return "%" + escape(keyword) + "%";
    }

    /** 역슬래시 자신을 <b>먼저</b> 바꿔야 한다. 나중에 하면 앞에서 넣은 이스케이프까지 다시 이스케이프된다. */
    public static String escape(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
