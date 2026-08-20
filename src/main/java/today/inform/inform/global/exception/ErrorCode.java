package today.inform.inform.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── Common ──────────────────────────────────────────────────────────────
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "유효하지 않은 입력 값입니다."),
    INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION", "허용되지 않은 상태 변경입니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
            "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요."),
    RESOURCE_BUSY(HttpStatus.CONFLICT, "RESOURCE_BUSY",
            "요청이 일시적으로 충돌했습니다. 잠시 후 다시 시도해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "존재하지 않는 경로입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않은 요청 방식입니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문을 해석할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // ── DB 제약 위반 (표준 SQLSTATE) ─────────────────────────────────────────
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "이미 존재하는 값입니다."),
    RELATED_RESOURCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "RELATED_RESOURCE_NOT_FOUND", "참조 대상이 존재하지 않습니다."),

    // ── DB 트리거 위반 (사용자 정의 SQLSTATE IN001~IN009) ────────────────────
    // POLICY 23장 매핑표와 1:1 대응한다. 여기를 고치면 그 표도 함께 고칠 것.
    IMMUTABLE_FIELD(HttpStatus.BAD_REQUEST, "IMMUTABLE_FIELD", "생성 후 변경할 수 없는 값입니다."),
    VENDOR_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "VENDOR_TYPE_MISMATCH", "공지 출처와 제공처 유형이 일치하지 않습니다."),
    MISSING_SOURCE_KEY(HttpStatus.BAD_REQUEST, "MISSING_SOURCE_KEY", "수집 공지는 출처 식별자와 URL이 필요합니다."),
    COMMENT_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "COMMENT_DEPTH_EXCEEDED", "답글에는 다시 답글을 달 수 없습니다."),
    INVALID_COMMENT_PARENT(HttpStatus.BAD_REQUEST, "INVALID_COMMENT_PARENT", "상위 댓글로 지정할 수 없는 댓글입니다."),
    INVALID_VENDOR_TYPE(HttpStatus.BAD_REQUEST, "INVALID_VENDOR_TYPE", "학과·기관만 선택할 수 있습니다."),
    CRAWLER_STORAGE_POLICY(HttpStatus.BAD_REQUEST, "CRAWLER_STORAGE_POLICY", "허용되지 않은 첨부 저장 방식입니다."),
    INACTIVE_CLUB_TYPE(HttpStatus.BAD_REQUEST, "INACTIVE_CLUB_TYPE", "비활성 동아리 유형은 선택할 수 없습니다."),
    NOT_CLUB_VENDOR(HttpStatus.BAD_REQUEST, "NOT_CLUB_VENDOR", "동아리 유형은 동아리 제공처에만 지정할 수 있습니다."),

    // ── Auth ────────────────────────────────────────────────────────────────
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN", "유효하지 않은 구글 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."),
    DOMAIN_RESTRICTED(HttpStatus.FORBIDDEN, "DOMAIN_RESTRICTED", "인하대학교 계정(@inha.edu, @inha.ac.kr)만 가입 가능합니다."),

    // ── User / 온보딩 ────────────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."),
    ONBOARDING_MIN_SELECTION(HttpStatus.BAD_REQUEST, "ONBOARDING_MIN_SELECTION", "최소 1개 이상 선택해야 합니다."),

    // ── Article ─────────────────────────────────────────────────────────────
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_NOT_FOUND", "존재하지 않는 공지입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    VENDOR_NOT_FOUND(HttpStatus.NOT_FOUND, "VENDOR_NOT_FOUND", "존재하지 않는 제공처입니다."),
    CLUB_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "CLUB_TYPE_NOT_FOUND", "존재하지 않는 동아리 유형입니다."),
    NOT_IN_TRASH(HttpStatus.BAD_REQUEST, "NOT_IN_TRASH", "휴지통 상태가 아닌 공지는 복구할 수 없습니다."),
    INVALID_STATUS_FOR_SOURCE(HttpStatus.BAD_REQUEST, "INVALID_STATUS_FOR_SOURCE",
            "공지 출처에서 사용할 수 없는 상태입니다."),
    INVALID_ARTICLE_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_ARTICLE_PERIOD",
            "시작일은 종료일보다 늦을 수 없습니다."),
    DUPLICATE_ARTICLE(HttpStatus.CONFLICT, "DUPLICATE_ARTICLE", "이미 존재하는 공지입니다."),
    SEARCH_KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "SEARCH_KEYWORD_TOO_SHORT", "검색어는 2글자 이상 입력해야 합니다."),
    INVALID_SORT_PROPERTY(HttpStatus.BAD_REQUEST, "INVALID_SORT_PROPERTY", "허용되지 않은 정렬 기준입니다."),

    // ── Comment / Announcement ──────────────────────────────────────────────
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "존재하지 않는 댓글입니다."),
    ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND", "존재하지 않는 서비스 공지입니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "존재하지 않는 알림입니다."),

    // ── File ────────────────────────────────────────────────────────────────
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "존재하지 않는 첨부파일입니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "허용되지 않는 파일 형식입니다. (허용: jpg, jpeg, png, gif, webp)"),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_SIZE_EXCEEDED", "파일 크기는 10MB를 초과할 수 없습니다."),
    FILE_IS_EMPTY(HttpStatus.BAD_REQUEST, "FILE_IS_EMPTY", "빈 파일은 업로드할 수 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
