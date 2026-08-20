package today.inform.inform.notification.entity;

/**
 * 알림 종류. DB CHECK({@code ck_notifications_type})가 이 두 가지만 허용합니다.
 *
 * <p>종류를 늘리려면 마이그레이션이 필요합니다. 의도적입니다 —
 * 알림은 사용자 × 공지로 곱해져 가장 빨리 커지는 테이블이라
 * 새 종류를 넣을 때 발송량을 한 번 더 생각하게 만듭니다.
 */
public enum NotificationType {

    /**
     * 마감 하루 전. 중복 방지 키는 <b>마감일</b>입니다.
     *
     * <p>마감이 연장되면 키가 바뀌어 자연히 다시 발송됩니다.
     * 공지 id 를 키로 썼다면 연장된 마감을 알릴 방법이 없었습니다.
     */
    DEADLINE_D1,

    /** 내 댓글에 답글. 중복 방지 키는 <b>답글 댓글 id</b>입니다. */
    COMMENT_REPLY
}
