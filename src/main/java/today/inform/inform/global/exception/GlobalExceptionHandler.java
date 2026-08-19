package today.inform.inform.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import today.inform.inform.global.response.ApiResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getCode(), message));
    }

    /**
     * DB 제약·트리거 위반을 400/409 계열로 변환한다.
     *
     * <p>이 핸들러가 없으면 "답글의 답글 금지" 같은 검증 실패가 전부 500 으로 나가고,
     * 프론트는 5xx 를 재시도 대상으로 다루므로 잘못된 입력을 무한 재시도한다.
     *
     * <p>매핑되지 않는 DB 오류는 진짜 장애이므로 500 으로 두되, 원문을 로그에 남긴다.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException e) {
        ErrorCode errorCode = SqlStateErrorMapper.resolve(e);

        if (errorCode == null) {
            log.error("Unmapped data access exception", e);
            return ResponseEntity
                    .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                    .body(ApiResponse.fail(
                            ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                            ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
        }

        // 트리거가 담은 한글 메시지가 ErrorCode 기본 문구보다 구체적이면 그것을 쓴다.
        String dbMessage = SqlStateErrorMapper.extractDbMessage(e);
        String message = (dbMessage != null && !dbMessage.isBlank())
                ? dbMessage
                : errorCode.getMessage();

        log.warn("DB constraint violation -> {} : {}", errorCode.getCode(), message);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), message));
    }

    /**
     * Spring MVC 가 던지는 표준 예외들을 제 상태 코드로 돌려보냅니다.
     *
     * <p>★ 이게 없으면 아래 {@code Exception} 핸들러가 <b>404 를 500 으로 바꿔버립니다.</b>
     * 존재하지 않는 경로 요청이 전부 서버 장애로 보이고, 프론트는 5xx 를
     * 재시도 대상으로 다루므로 오타 경로에 재시도가 붙습니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 본문이 JSON 이 아니거나 타입이 맞지 않는 경우. 400 이어야 합니다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return toResponse(ErrorCode.MALFORMED_REQUEST);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.fail(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
