package today.inform.inform.calendar.dto.request;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * CAL-01 / CAL-02 조회 조건.
 *
 * <p><b>{@code year}·{@code month} 를 여기서 검증합니다.</b>
 * {@code LocalDate.of(2026, 13, 1)} 은 {@code DateTimeException} 을 던지는데,
 * 그건 SQLSTATE 가 없어 {@code GlobalExceptionHandler} 의 마지막 그물에 걸려 <b>500</b> 으로 나갑니다.
 * 잘못된 입력이 5xx 로 나가면 프론트가 재시도 대상으로 다뤄 무한 재시도가 됩니다.
 *
 * @param myMajorOnly CAL-02. 로그인해야 의미가 있습니다 — 구독 학과가 있어야 거를 수 있습니다
 */
public record CalendarQuery(
        int year,
        int month,
        List<Long> categoryIds,
        boolean myMajorOnly) {

    /**
     * 달력이 다룰 수 있는 연도 범위.
     *
     * <p>넓게 잡되 무한은 아닙니다. {@code year=999999999} 같은 값이 들어오면
     * 날짜 계산이 오버플로로 터집니다.
     */
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    public CalendarQuery {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "연도는 " + MIN_YEAR + "~" + MAX_YEAR + " 사이여야 합니다.");
        }
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 1~12 사이여야 합니다.");
        }
    }

    public LocalDate monthStart() {
        return YearMonth.of(year, month).atDay(1);
    }

    public LocalDate monthEnd() {
        return YearMonth.of(year, month).atEndOfMonth();
    }
}
