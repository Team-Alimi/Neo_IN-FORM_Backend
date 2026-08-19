package today.inform.inform.user.entity;

/**
 * DB 는 varchar(20) + CHECK 로 저장합니다 (native enum 미사용).
 * 값 집합을 바꾸면 마이그레이션의 CHECK 와 user_role_logs 의 CHECK 도 함께 고쳐야 합니다.
 */
public enum UserRole {
    USER,
    ADMIN;

    /** Spring Security 권한 문자열. {@code hasRole("ADMIN")} 은 ROLE_ 접두사를 요구합니다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
