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
            @RequestParam(required = false) List<String> categories,
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(calendarService.getMonthlyNotices(year, month, categories, userId));
    }

    @GetMapping("/daily-notices")
    public ApiResponse<CalendarDailyListResponse> getDailyNotices(
            @RequestParam LocalDate date,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(defaultValue = "1") Integer page,
            @AuthenticationPrincipal Integer userId
    ) {
        return ApiResponse.success(calendarService.getDailyNotices(date, categories, page, userId));
    }
}
