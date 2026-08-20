package today.inform.inform.article.entity;

import java.util.Set;

/**
 * 공지 상태. POLICY 6장 "상태 전이 규칙" 을 그대로 옮겼습니다.
 *
 * <p><b>DB 와 앱의 역할 분담</b>
 * <ul>
 *   <li>DB — 상태 <b>집합</b>만 검사합니다. {@code ck_articles_status_by_source} 가
 *       SCHOOL 에 DRAFT 를, CLUB 에 PENDING_REVIEW 를 넣지 못하게 막습니다.</li>
 *   <li>앱 — 상태 <b>전이</b>(A→B 허용 여부)를 검사합니다. DB 는 이걸 모릅니다.</li>
 * </ul>
 * 둘 중 하나만 있으면 구멍이 납니다. DB 만 있으면 검수를 건너뛴 발행이 통과하고,
 * 앱만 있으면 크롤러가 직접 쓴 행을 아무도 검사하지 않습니다.
 *
 * <p><b>원칙 — 역방향은 전부 허용, 단계 건너뛰기만 금지.</b>
 * 반려·배포 취소는 운영에서 흔한 동작이라 막으면 관리자가 우회 경로를 찾게 됩니다.
 * 반대로 PENDING_REVIEW → PUBLISHED 를 허용하면 검수 단계가 있으나 마나가 됩니다.
 *
 * <p><b>단, 동아리 공지에는 배포 취소가 없습니다.</b>
 * 학교 공지는 PUBLISHED → READY_TO_PUBLISH 로 내려올 수 있지만,
 * 동아리 공지가 PUBLISHED 에서 갈 수 있는 곳은 TRASHED 뿐입니다.
 * POLICY 전이 표에 {@code PUBLISHED → DRAFT} 가 없고 팀이 표를 그대로 따르기로 했습니다.
 * 발행한 동아리 공지를 내리려면 휴지통으로 보낸 뒤 복구하는 경로를 씁니다.
 */
public enum ArticleStatus {

    /** SCHOOL 신규 수집분·크롤러 강등분. 관리자 검수 대기. */
    PENDING_REVIEW(SourceType.SCHOOL),

    /** SCHOOL 검수 완료. 발행만 남은 상태. */
    READY_TO_PUBLISH(SourceType.SCHOOL),

    /** CLUB 작성 중. */
    DRAFT(SourceType.CLUB),

    /** 서비스 노출 중. {@code published_at} 이 반드시 있어야 합니다(DB CHECK). */
    PUBLISHED(SourceType.SCHOOL, SourceType.CLUB),

    /** 휴지통. 어느 상태에서든 올 수 있고, 복구는 감사 로그의 직전 상태로만 갑니다. */
    TRASHED(SourceType.SCHOOL, SourceType.CLUB);

    private final Set<SourceType> allowedSources;

    /**
     * 전이 대상. TRASHED 는 여기 넣지 않습니다 — 모든 상태에서 허용되므로
     * 표에 다섯 번 적는 대신 {@link #canTransitionTo} 에서 한 번 처리합니다.
     */
    private Set<ArticleStatus> nextStates;

    ArticleStatus(SourceType... allowedSources) {
        this.allowedSources = Set.of(allowedSources);
    }

    static {
        // enum 상수끼리 참조해야 해서 생성자에서 못 만듭니다. static 블록에서 한 번만 채웁니다.
        PENDING_REVIEW.nextStates   = Set.of(READY_TO_PUBLISH);
        READY_TO_PUBLISH.nextStates = Set.of(PUBLISHED, PENDING_REVIEW);   // 발행 / 반려
        // ★ DRAFT 를 넣지 않습니다. POLICY 전이 표에 PUBLISHED → DRAFT 가 없고,
        //   팀 결정도 "표 그대로" 였습니다.
        //   아래 두 상태는 SCHOOL 전용이라 isAllowedFor 가 CLUB 을 걸러냅니다.
        //   결과적으로 발행된 동아리 공지가 갈 수 있는 곳은 TRASHED 뿐입니다 — 의도된 제약입니다.
        PUBLISHED.nextStates        = Set.of(READY_TO_PUBLISH, PENDING_REVIEW);
        DRAFT.nextStates            = Set.of(PUBLISHED);
        TRASHED.nextStates          = Set.of();   // 복구는 restore 경로로만
    }

    /** {@code ck_articles_status_by_source} 와 같은 내용. 앱에서 먼저 걸러 400 으로 돌려줍니다. */
    public boolean isAllowedFor(SourceType sourceType) {
        return allowedSources.contains(sourceType);
    }

    /**
     * 이 상태에서 {@code next} 로 갈 수 있는지.
     *
     * <p>TRASHED 로 가는 건 어느 상태에서든 됩니다. 반대로 TRASHED 에서 나오는 건
     * 여기서 전부 막습니다 — 복구는 감사 로그가 기억하는 직전 상태로만 가야 하고,
     * 그건 이 enum 이 알 수 없는 정보입니다. {@code restore} 경로가 따로 처리합니다.
     */
    public boolean canTransitionTo(ArticleStatus next) {
        if (next == this) {
            return false;   // 무의미한 전이. 감사 로그만 더럽힙니다
        }
        if (next == TRASHED) {
            return true;
        }
        return nextStates.contains(next);
    }

    /** 서비스 목록에 노출되는 상태인지. */
    public boolean isVisibleToUsers() {
        return this == PUBLISHED;
    }
}
