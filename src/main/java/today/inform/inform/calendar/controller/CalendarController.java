package today.inform.inform.calendar.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.calendar.dto.request.CalendarQuery;
import today.inform.inform.calendar.service.CalendarService;
import today.inform.inform.global.response.ApiResponse;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * CAL-01 / CAL-02 캘린더. <b>비로그인도 열립니다</b>({@code SecurityConfig}).
 *
 * <p>여기서 공지를 눌러 상세로 넘어가면 로그인이 필요합니다 — 의도된 동작입니다.
 * 달력은 서비스를 처음 보는 사람에게 "무엇이 올라오는지" 를 보여 주는 입구 역할입니다.
 */
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 해당 월과 기간이 겹치는 배포 공지. <b>페이징이 없습니다</b>(명세).
     *
     * @param principal   비로그인이면 {@code null} 입니다
     * @param myMajorOnly CAL-02. 구독 학과·기관의 공지만. 로그인하지 않으면 401
     */
    @GetMapping("/articles")
    public ApiResponse<List<ArticleSummaryResponse>> monthly(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "year") int year,
            @RequestParam(name = "month") int month,
            @RequestParam(name = "category_id", required = false) List<Long> categoryIds,
            @RequestParam(name = "my_major_only", defaultValue = "false") boolean myMajorOnly) {

        CalendarQuery query = new CalendarQuery(year, month, categoryIds, myMajorOnly);
        return ApiResponse.success(calendarService.findMonthly(query, principal));
    }
}
