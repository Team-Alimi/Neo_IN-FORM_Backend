package today.inform.inform.global.response;

/**
 * 모든 API 응답의 공통 래퍼.
 * 성공: { "success": true,  "data": ..., "error": null }
 * 실패: { "success": false, "data": null, "error": { "code": ..., "message": ... } }
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}
