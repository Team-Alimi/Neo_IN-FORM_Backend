package today.inform.inform.article.repository;

/**
 * 공지에 대한 사용자 반응 junction 2종.
 *
 * <p>두 테이블이 {@code (user_id, article_id)} 복합 PK 로 모양이 같고,
 * 개수를 세는 트리거도 {@code articles} 위에 나란히 걸려 있습니다.
 * 개인화 junction 을 {@code PreferenceType} 하나로 다루는 것과 같은 이유로 묶습니다.
 *
 * <p>테이블명은 이 enum 의 상수이며 외부 입력이 들어오지 않습니다.
 */
public enum ReactionType {

    /** BMK. 저장해 둔 공지. */
    BOOKMARK("bookmarks"),

    /** LIK. 계정당 1표. */
    LIKE("article_likes");

    private final String table;

    ReactionType(String table) {
        this.table = table;
    }

    public String table() {
        return table;
    }
}
