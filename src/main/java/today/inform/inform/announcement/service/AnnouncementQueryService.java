package today.inform.inform.announcement.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.announcement.dto.response.AnnouncementDetail;
import today.inform.inform.announcement.dto.response.AnnouncementSummary;
import today.inform.inform.announcement.repository.AnnouncementRepository;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 사용자용 서비스 공지 조회. <b>전부 비로그인으로 열려 있습니다</b>
 * ({@code SecurityConfig} 의 {@code /announcements}, {@code /announcements/*}).
 *
 * <p>점검 공지는 로그인이 안 되는 상황에서 가장 필요합니다. 인증을 걸면
 * "로그인이 안 됩니다" 를 알리는 공지를 로그인해야 볼 수 있게 됩니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementQueryService {

    private final AnnouncementRepository announcementRepository;

    /**
     * 공지 목록.
     *
     * <p><b>클라이언트 정렬을 버립니다.</b> {@code page}·{@code size} 만 받고
     * {@code sort} 는 무시합니다 — 쿼리가 ORDER BY 를 고정하고 있고, Spring Data 는
     * 클라이언트의 {@code sort} 를 검증 없이 그 뒤에 이어 붙이기 때문입니다.
     * {@code ?sort=foo} 하나면 JPQL 파싱에서 터져 500 이 나갑니다.
     */
    public Page<AnnouncementSummary> list(Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return announcementRepository.findVisible(today(), unsorted)
                .map(AnnouncementSummary::from);
    }

    /**
     * 공지 상세.
     *
     * <p>노출 조건을 통과하지 못하면 {@code ANNOUNCEMENT_NOT_FOUND}(404)입니다.
     * 임시저장·보관 공지와 노출 기간 밖의 공지가 모두 여기에 해당합니다 —
     * 이유를 나누어 알려 주면 "번호 N 에는 아직 안 알린 공지가 있다" 가 새어 나갑니다.
     */
    public AnnouncementDetail detail(Long announcementId) {
        return announcementRepository.findVisibleById(announcementId, today())
                .map(AnnouncementDetail::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }

    /**
     * 지금 띄울 팝업.
     *
     * <p><b>없으면 빈 배열입니다.</b> 404 가 아닙니다 — "띄울 것이 없음" 은 정상이고,
     * 앱 진입마다 부르는 호출이라 오류로 다루면 클라이언트가 재시도하게 됩니다.
     *
     * <p>여러 건이 올 수 있습니다. "다시 보지 않기" 는 서버가 기억하지 않으므로
     * 프론트가 {@code id} 별로 걸러야 합니다({@link AnnouncementDetail} 참고).
     */
    public List<AnnouncementDetail> popups() {
        return announcementRepository.findPopups(today()).stream()
                .map(AnnouncementDetail::from)
                .toList();
    }

    /**
     * 노출 판정의 기준일.
     *
     * <p>앱 시계입니다({@code -Duser.timezone=Asia/Seoul} 이라 KST). SQL 의
     * {@code CURRENT_DATE} 를 쓰면 DB 컨테이너(UTC)와 KST 00~09시에 하루 어긋납니다.
     */
    private static LocalDate today() {
        return LocalDate.now();
    }
}
