package today.inform.inform.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "유효하지 않은 입력 값입니다."),
    INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION", "허용되지 않은 상태 변경입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // Auth
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN", "유효하지 않은 구글 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."),
    DOMAIN_RESTRICTED(HttpStatus.FORBIDDEN, "DOMAIN_RESTRICTED", "인하대학교 계정(@inha.edu, @inha.ac.kr)만 가입 가능합니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."),

    // Article
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_NOT_FOUND", "존재하지 않는 공지입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    VENDOR_NOT_FOUND(HttpStatus.NOT_FOUND, "VENDOR_NOT_FOUND", "존재하지 않는 제공처입니다."),
    NOT_IN_TRASH(HttpStatus.BAD_REQUEST, "NOT_IN_TRASH", "휴지통 상태가 아닌 공지는 복구할 수 없습니다."),
    DUPLICATE_ARTICLE(HttpStatus.CONFLICT, "DUPLICATE_ARTICLE", "이미 존재하는 공지입니다."),

    // File
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "존재하지 않는 첨부파일입니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "허용되지 않는 파일 형식입니다. (허용: jpg, jpeg, png, gif, webp)"),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_SIZE_EXCEEDED", "파일 크기는 10MB를 초과할 수 없습니다."),
    FILE_IS_EMPTY(HttpStatus.BAD_REQUEST, "FILE_IS_EMPTY", "빈 파일은 업로드할 수 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
