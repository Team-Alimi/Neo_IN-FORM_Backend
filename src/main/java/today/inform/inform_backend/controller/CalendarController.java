package today.inform.inform_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import today.inform.inform_backend.common.response.ApiResponse;
import today.inform.inform_backend.dto.CalendarDailyListResponse;
import today.inform.inform_backend.dto.CalendarNoticeResponse;
import today.inform.inform_backend.service.CalendarService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/notices")
    public ApiResponse<List<CalendarNoticeResponse>> getMonthlyNotices(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(name = "category_id", required = false) List<Integer> categoryIds,
            @RequestParam(name = "is_my_only", required = false) Boolean isMyOnly,
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(calendarService.getMonthlyNotices(year, month, categoryIds, isMyOnly, userId));
    }

    @GetMapping("/daily-notices")
    public ApiResponse<CalendarDailyListResponse> getDailyNotices(
            @RequestParam LocalDate date,
            @RequestParam(name = "category_id", required = false) List<Integer> categoryIds,
            @RequestParam(name = "is_my_only", required = false) Boolean isMyOnly,
            @RequestParam(defaultValue = "1") Integer page,
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(calendarService.getDailyNotices(date, categoryIds, isMyOnly, page, userId));
    }
}
