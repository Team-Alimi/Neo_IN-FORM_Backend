package today.inform.inform.notification.service;

import java.text.BreakIterator;
import java.util.Locale;
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

    /**
     * CMT-05 답글 알림. <b>댓글 작성 트랜잭션 안에서 호출됩니다.</b>
     *
     * <p>수신자 판정·중복 방지·탈퇴 확인이 전부 쿼리 한 문장에 있습니다.
     * 알림을 못 만들었다고 댓글 작성이 실패하면 안 되지만,
     * 여기서 예외를 삼키지는 않습니다 — 이 INSERT 가 실패할 수 있는 경우는
     * DB 장애뿐이고 그때는 댓글 INSERT 도 이미 실패했을 것이기 때문입니다.
     * 억지로 감싸면 진짜 장애가 조용히 묻힙니다.
     */
    @Transactional
    public void notifyReply(Long parentCommentId, Long replyId, Long actorId,
                            String articleTitle, String replyContent) {
        notificationRepository.createReplyNotification(
                parentCommentId, replyId, actorId,
                "내 댓글에 답글이 달렸습니다",
                summarize(articleTitle, replyContent));
    }

    /** 알림 미리보기에 담을 답글 길이. */
    private static final int PREVIEW_LENGTH = 60;

    /**
     * 알림 본문. 답글 원문을 짧게 잘라 넣습니다.
     *
     * <p>전문을 담지 않는 이유는 알림 테이블이 사용자 × 공지로 곱해져
     * 가장 빨리 커지기 때문입니다. 자세한 내용은 눌러서 보면 됩니다.
     */
    private String summarize(String articleTitle, String replyContent) {
        return "[" + articleTitle + "] " + truncate(replyContent);
    }

    /**
     * 글자 경계에서 자릅니다.
     *
     * <p><b>{@code substring(0, 60)} 을 쓰면 안 됩니다.</b> Java 문자열의 길이는 UTF-16 단위라
     * 이모지 한 글자가 두 칸을 차지합니다. 경계가 그 사이에 떨어지면 짝을 잃은 조각이 남고,
     * UTF-8 로 인코딩할 수 없어 <b>드라이버가 조용히 {@code '?'} 로 바꿉니다.</b>
     * 예외가 없어서 깨진 글자가 그대로 저장되고 로그에도 남지 않습니다.
     *
     * <p>{@link BreakIterator} 를 쓰는 이유는 코드 포인트로도 부족하기 때문입니다 —
     * 👍🏽 는 이모지와 피부톤 두 코드 포인트고, 🇰🇷 는 지역 표시 문자 두 개입니다.
     * 코드 포인트 경계에서 자르면 피부톤이 떨어져 나가거나 국기가 반쪽이 됩니다.
     * 사람이 한 글자로 보는 단위에서 잘라야 자연스럽습니다.
     */
    private static String truncate(String text) {
        if (text.length() <= PREVIEW_LENGTH) {
            return text;
        }
        BreakIterator boundaries = BreakIterator.getCharacterInstance(Locale.ROOT);
        boundaries.setText(text);

        int end = boundaries.isBoundary(PREVIEW_LENGTH)
                ? PREVIEW_LENGTH
                : boundaries.preceding(PREVIEW_LENGTH);
        if (end == BreakIterator.DONE || end <= 0) {
            // 첫 글자부터 경계가 없는 경우는 없지만, 여기서 0 이 되면 본문이 통째로 사라집니다.
            end = PREVIEW_LENGTH;
        }
        return text.substring(0, end) + "…";
    }
}
