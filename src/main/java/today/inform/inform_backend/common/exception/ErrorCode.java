package today.inform.inform_backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "유효하지 않은 입력 값입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),

    // Auth & User
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN", "유효하지 않은 구글 토큰입니다."),
    DOMAIN_RESTRICTED(HttpStatus.FORBIDDEN, "DOMAIN_RESTRICTED", "인하대학교 계정(@inha.edu, @inha.ac.kr)만 가입 가능합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."),

    // Article
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_NOT_FOUND", "존재하지 않는 게시글입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    ALREADY_EXIST_ARTICLE(HttpStatus.CONFLICT, "ALREADY_EXIST_ARTICLE", "이미 존재하는 게시글입니다."),
    NOT_IN_GARBAGE(HttpStatus.BAD_REQUEST, "NOT_IN_GARBAGE", "휴지통 상태가 아닌 게시글은 복구할 수 없습니다."),

    // File Upload
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "허용되지 않는 파일 형식입니다. (허용: jpg, jpeg, png, gif, webp)"),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_SIZE_EXCEEDED", "파일 크기는 10MB를 초과할 수 없습니다."),
    FILE_IS_EMPTY(HttpStatus.BAD_REQUEST, "FILE_IS_EMPTY", "빈 파일은 업로드할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
