package today.inform.inform.announcement.entity;

import java.util.Set;

/**
 * 서비스 공지 상태. {@code ck_ann_status} 와 같은 집합입니다.
 *
 * <p><b>DB 와 앱의 역할 분담은 {@code ArticleStatus} 와 같습니다.</b>
 * DB 는 집합({@code ck_ann_status})과 불변식({@code ck_ann_published})만 보고,
 * 전이 허용 여부는 앱이 판단합니다.
 *
 * <p><b>DRAFT 로는 되돌아가지 못합니다.</b> 한 번 발행한 공지를 임시저장으로 내리면
 * 사용자가 이미 본 안내가 "없던 일" 이 되는데, 공지의 성격상 그건 ARCHIVED
 * (= 지난 안내)가 맞습니다. 내용을 고치는 것은 상태 전이가 아니라 수정(PATCH)이고,
 * 그건 어느 상태에서나 됩니다.
 *
 * <table>
 *   <caption>전이 표</caption>
 *   <tr><th>현재</th><th>갈 수 있는 곳</th><th>쓰임</th></tr>
 *   <tr><td>DRAFT</td><td>PUBLISHED, ARCHIVED</td><td>발행 / 쓰다 만 것 접기</td></tr>
 *   <tr><td>PUBLISHED</td><td>ARCHIVED</td><td>안내 종료</td></tr>
 *   <tr><td>ARCHIVED</td><td>PUBLISHED</td><td>잘못 내린 것 되살리기</td></tr>
 * </table>
 */
public enum AnnouncementStatus {

    /** 작성 중. 사용자에게 보이지 않습니다. */
    DRAFT,

    /** 노출 중. {@code published_at} 이 반드시 있어야 합니다({@code ck_ann_published}). */
    PUBLISHED,

    /** 지난 안내. 목록·팝업에서 빠지지만 기록은 남습니다. */
    ARCHIVED;

    private Set<AnnouncementStatus> nextStates;

    static {
        // enum 상수끼리 참조해야 해서 생성자에서 못 만듭니다. static 블록에서 한 번만 채웁니다.
        DRAFT.nextStates     = Set.of(PUBLISHED, ARCHIVED);
        PUBLISHED.nextStates = Set.of(ARCHIVED);
        ARCHIVED.nextStates  = Set.of(PUBLISHED);
    }

    /**
     * 이 상태에서 {@code next} 로 갈 수 있는지.
     *
     * <p>같은 상태로의 전이는 막습니다. 멱등하게 통과시키면 "발행 눌렀는데 아무 일도
     * 안 일어난" 경우와 "이미 발행돼 있던" 경우를 호출한 쪽이 구분할 수 없습니다.
     */
    public boolean canTransitionTo(AnnouncementStatus next) {
        return nextStates.contains(next);
    }

    /** 사용자 목록·팝업에 나가는 상태인지. */
    public boolean isVisibleToUsers() {
        return this == PUBLISHED;
    }
}
