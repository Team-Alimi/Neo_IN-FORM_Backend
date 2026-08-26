package today.inform.inform.calendar.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.dto.response.ArticleSummaryResponse;
import today.inform.inform.article.repository.ArticleQueryRepository;
import today.inform.inform.calendar.dto.request.CalendarQuery;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.global.security.AuthPrincipal;

/**
 * CAL-01 월간 일정 · CAL-02 내 학과 필터.
 *
 * <p><b>비로그인도 볼 수 있는 유일한 공지 목록입니다</b>({@code SecurityConfig} 의 {@code /calendar/**}).
 * 그래서 {@code principal} 이 {@code null} 로 들어옵니다 —
 * 다른 조회 서비스는 전부 로그인 뒤라 그 경우를 다루지 않습니다.
 * 여기서 빠뜨리면 <b>게스트의 첫 화면이 NPE 로 500</b> 이 됩니다.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    /**
     * 페이징이 없는 목록의 안전 상한.
     *
     * <p>명세가 페이징을 두지 않았고 달력 한 화면이 한 달치를 통째로 그리기 때문입니다.
     * 상한이 없으면 요청 하나가 그달 공지 전부와 그만큼의 제공처·카테고리를 긁어갑니다.
     * 실제 한 달 배포량(수십 건)보다 넉넉하되 사고를 막는 선입니다.
     */
    private static final int MAX_RESULTS = 300;

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> findMonthly(CalendarQuery query, AuthPrincipal principal) {
        Long userId = (principal == null) ? null : principal.userId();
        requireLoginForPersonalFilters(query, userId);

        return articleQueryRepository.findForCalendar(
                query.monthStart(), query.monthEnd(),
                query.categoryIds(), query.myMajorOnly(), query.interestOnly(),
                userId, MAX_RESULTS);
    }

    /**
     * 개인화 필터("내 학과만" · "관심 카테고리만")는 로그인해야 합니다.
     *
     * <p>비로그인 상태로 통과시키면 구독·관심사가 없으니 <b>빈 목록</b>이 나갑니다.
     * 그건 "이번 달에 그런 공지가 없다" 와 구분되지 않아서, 사용자는 로그인이 필요한 줄 모른 채
     * 공지가 없다고 믿게 됩니다. 조용히 틀린 답을 주느니 401 이 낫습니다.
     */
    private static void requireLoginForPersonalFilters(CalendarQuery query, Long userId) {
        if (userId != null) {
            return;
        }
        if (query.myMajorOnly()) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED, "내 학과 공지만 보려면 로그인이 필요합니다.");
        }
        if (query.interestOnly()) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED, "관심 카테고리 공지만 보려면 로그인이 필요합니다.");
        }
    }
}
