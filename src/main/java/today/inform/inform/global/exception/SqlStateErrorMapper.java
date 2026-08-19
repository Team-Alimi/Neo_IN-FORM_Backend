package today.inform.inform.global.exception;

import java.sql.SQLException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * DB 가 던진 예외의 SQLSTATE 를 API ErrorCode 로 옮긴다.
 *
 * <p>이게 없으면 DB 가 막아준 모든 규칙이 클라이언트에게 500 으로 나간다.
 * plpgsql 의 맨 {@code RAISE EXCEPTION} 은 SQLSTATE 가 전부 {@code P0001} 로 뭉개지므로,
 * 각 트리거에 {@code USING ERRCODE = 'INxxx'} 로 고유 코드를 부여해 두었다
 * (마이그레이션 {@code V3__triggers.sql} 참조).
 *
 * <p>프론트는 5xx 를 재시도 대상으로 다루기 때문에, 잘못된 입력이 500 으로 나가면
 * 무한 재시도로 이어진다. 매핑은 선택이 아니라 필수다.
 *
 * <p>POLICY 23장 매핑표와 1:1 로 대응한다. 한쪽만 고치면 안 된다.
 */
public final class SqlStateErrorMapper {

    private static final Map<String, ErrorCode> BY_SQL_STATE = Map.ofEntries(
            // 사용자 정의 — V3__triggers.sql
            Map.entry("IN001", ErrorCode.IMMUTABLE_FIELD),
            Map.entry("IN002", ErrorCode.VENDOR_TYPE_MISMATCH),
            Map.entry("IN003", ErrorCode.MISSING_SOURCE_KEY),
            Map.entry("IN004", ErrorCode.COMMENT_DEPTH_EXCEEDED),
            Map.entry("IN005", ErrorCode.INVALID_COMMENT_PARENT),
            Map.entry("IN006", ErrorCode.INVALID_VENDOR_TYPE),
            Map.entry("IN007", ErrorCode.CRAWLER_STORAGE_POLICY),
            Map.entry("IN008", ErrorCode.INACTIVE_CLUB_TYPE),
            Map.entry("IN009", ErrorCode.NOT_CLUB_VENDOR),

            // 표준 SQLSTATE
            Map.entry("23505", ErrorCode.DUPLICATE_RESOURCE),          // unique_violation
            Map.entry("23514", ErrorCode.INVALID_INPUT_VALUE),         // check_violation
            Map.entry("23503", ErrorCode.RELATED_RESOURCE_NOT_FOUND),  // foreign_key_violation
            Map.entry("23502", ErrorCode.INVALID_INPUT_VALUE)          // not_null_violation
    );

    private SqlStateErrorMapper() {
    }

    /**
     * 예외 체인을 훑어 첫 번째로 매핑 가능한 SQLSTATE 를 찾는다.
     * Spring 이 감싼 예외 안쪽에 원본 {@link SQLException} 이 들어 있으므로 원인을 따라간다.
     *
     * @return 매핑되는 ErrorCode. 없으면 {@code null} — 호출자가 500 으로 처리한다.
     */
    @Nullable
    public static ErrorCode resolve(@Nullable Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlException) {
                ErrorCode mapped = BY_SQL_STATE.get(sqlException.getSQLState());
                if (mapped != null) {
                    return mapped;
                }
            }
            if (t.getCause() == t) {
                break;  // 자기 자신을 원인으로 갖는 예외 방어
            }
        }
        return null;
    }

    /**
     * 트리거가 {@code RAISE EXCEPTION} 에 담은 한글 메시지를 꺼낸다.
     * 사용자에게 그대로 보여줄 수 있을 만큼 구체적인 문구를 쓰고 있으므로 재활용한다.
     *
     * <p><b>{@code IN0xx} 일 때만 꺼낸다.</b> 표준 SQLSTATE(23503 등)의 메시지는
     * PostgreSQL 이 만든 영문이고 제약 이름({@code fk_uv_vendor})까지 들어 있다.
     * 사용자에게 쓸모가 없을뿐더러 스키마 내부 구조를 그대로 노출한다.
     * 그런 경우는 {@link ErrorCode} 의 기본 문구가 낫다.
     */
    @Nullable
    public static String extractDbMessage(@Nullable Throwable throwable) {
        // 자기 자신을 원인으로 갖는 예외에 대비해 갱신식에서 끊는다.
        for (Throwable t = throwable; t != null; t = (t.getCause() == t ? null : t.getCause())) {
            if (t instanceof SQLException sqlException
                    && isCustomSqlState(sqlException.getSQLState())) {
                String message = sqlException.getMessage();
                if (message != null && !message.isBlank()) {
                    // PostgreSQL 은 여러 줄(Detail/Hint)을 붙여 보내므로 첫 줄만 쓴다
                    int newline = message.indexOf('\n');
                    String firstLine = (newline > 0 ? message.substring(0, newline) : message).trim();
                    return stripSeverityPrefix(firstLine);
                }
            }
        }
        return null;
    }

    /** 우리가 트리거에 부여한 코드인지. {@code IN001}~{@code IN009}. */
    private static boolean isCustomSqlState(@Nullable String sqlState) {
        return sqlState != null && sqlState.startsWith("IN");
    }

    /**
     * JDBC 드라이버가 붙이는 {@code "ERROR: "} 접두어를 떼어낸다.
     * 그대로 두면 응답 메시지가 {@code "ERROR: 구독 대상은 ..."} 으로 나간다.
     */
    private static String stripSeverityPrefix(String message) {
        int colon = message.indexOf(": ");
        // 접두어는 영문 대문자 한 단어다. 한글 본문에 콜론이 있어도 잘리지 않게 확인한다.
        if (colon > 0 && message.substring(0, colon).chars().allMatch(Character::isUpperCase)) {
            return message.substring(colon + 2).trim();
        }
        return message;
    }
}
