package today.inform.inform.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;
import today.inform.inform.notification.dto.response.NotificationResponse;
import today.inform.inform.notification.repository.NotificationRepository;

/**
 * NTF-01 ~ NTF-04, CMT-05.
 *
 * <p>알림은 <b>읽고 지우는 자원</b>입니다. 사용자가 만들 수 있는 것이 없고,
 * 생성은 전부 시스템(답글·마감 배치)이 합니다. 그래서 이 서비스에는
 * 사용자 입력을 검증할 것이 거의 없고, 대신 "남의 알림을 건드리지 못하게" 가 전부입니다.
 * 그 확인은 모두 {@code WHERE user_id = ?} 로 쿼리 안에 들어가 있습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** NTF-01. 최신순 고정이라 클라이언트 정렬은 버립니다. */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return notificationRepository
                .findPage(userId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(NotificationResponse::from);
    }

    /** NTF-02 배지 개수. */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /**
     * NTF-03 개별 읽음. 멱등입니다.
     *
     * <p>남의 알림이면 404 입니다. 403 을 쓰면 "그 번호의 알림이 존재한다" 가 새어 나가는데,
     * 알림은 목록에 공개되는 자원이 아니라 감출 이유가 있습니다.
     */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        if (notificationRepository.markRead(notificationId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    /**
     * NTF-04 전체 읽음.
     *
     * @return 이번에 읽음 처리된 개수
     */
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }
}
