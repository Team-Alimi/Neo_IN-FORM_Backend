package today.inform.inform.article.entity;

/**
 * 공지의 출처. 생성 후 변경할 수 없습니다 — 트리거 {@code trg_articles_10_immutable} 이 IN001 로 막습니다.
 *
 * <p>상태 집합·검수 흐름·출처 무결성 규칙이 모두 이 값에 걸려 있어서
 * 뒤늦게 바꾸면 이미 붙은 {@code article_vendors} 와 앞뒤가 맞지 않게 됩니다.
 */
public enum SourceType {

    /** 학교·학과 공지. 크롤러가 수집하고 관리자가 검수합니다. */
    SCHOOL,

    /** 동아리 공지. 관리자가 직접 작성합니다. */
    CLUB
}
