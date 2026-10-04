package today.inform.inform.announcement.entity;

/**
 * 서비스 공지 분류. {@code ck_ann_type} 과 같은 집합입니다.
 *
 * <p><b>공지(announcement)와 공지사항(article)은 다릅니다.</b>
 * 이쪽은 <b>운영팀이 앱 사용자에게 직접 하는 안내</b>(점검·업데이트 등)이고,
 * {@code articles} 는 학교·동아리에서 <b>수집해 온 글</b>입니다.
 * 이름이 비슷해서 자주 헷갈리는데 테이블도 화면도 완전히 별개입니다.
 *
 * <p>값은 화면에서 배지 색을 고르는 데 쓰입니다. 동작 차이는 없습니다 —
 * 점검 공지가 팝업으로 뜨고 이벤트 공지가 안 뜨는 식의 규칙은 유형이 아니라
 * {@code is_popup} 이 정합니다. 유형으로 동작을 바꾸면 "이벤트인데 꼭 띄워야 하는"
 * 경우에 유형을 거짓으로 적게 됩니다.
 */
public enum AnnouncementType {

    /** 서비스 점검. */
    MAINTENANCE,

    /** 앱 업데이트 안내. */
    UPDATE,

    /** 이벤트. */
    EVENT,

    /** 그 밖의 안내. */
    GENERAL
}
