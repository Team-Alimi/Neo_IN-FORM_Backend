package today.inform.inform.article.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import today.inform.inform.global.entity.BaseTimeEntity;
import today.inform.inform.global.exception.BusinessException;
import today.inform.inform.global.exception.ErrorCode;

/**
 * 공지. 서비스의 중심 테이블이고, <b>앱이 유일한 작성자가 아닌</b> 유일한 엔티티입니다.
 *
 * <p>크롤러(별도 프로세스, {@code inform_crawler} 롤)가 같은 테이블에 직접 INSERT/UPDATE 하고,
 * DB 트리거가 그 위에서 상태·요약·카운터를 다시 손봅니다.
 * 그래서 이 매핑의 핵심은 "무엇을 쓰느냐" 가 아니라 <b>"무엇을 쓰지 않느냐"</b> 입니다.
 *
 * <h2>컬럼 소유권</h2>
 * <table border="1">
 *   <tr><th>컬럼</th><th>소유</th><th>매핑</th></tr>
 *   <tr><td>{@code search_text}, {@code period}</td><td>DB GENERATED</td><td>매핑하지 않음</td></tr>
 *   <tr><td>{@code *_count}</td><td>트리거·배치</td><td>읽기 전용</td></tr>
 *   <tr><td>{@code summary}</td><td>AI 파이프라인 + 무효화 트리거</td><td>읽기 전용</td></tr>
 *   <tr><td>{@code published_at}</td><td>트리거(V6)</td><td>읽기 전용</td></tr>
 *   <tr><td>{@code created_at}, {@code updated_at}</td><td>DEFAULT·트리거</td><td>읽기 전용</td></tr>
 *   <tr><td>나머지</td><td>앱·크롤러</td><td>쓰기 가능</td></tr>
 * </table>
 *
 * <h2>★ 낙관적 잠금과 {@code @Generated}</h2>
 * 이 엔티티에는 <b>UPDATE 시점 {@code @Generated} 프로퍼티가 하나도 없어야 합니다.</b>
 * 하나라도 있으면 Hibernate 가 UPDATE 를 {@code ... RETURNING} 델리게이트 경로로 돌리는데,
 * 그 경로는 갱신 행 수를 확인하지 않아 {@link Version} 이 무력화됩니다.
 * 관리자 두 명이 같은 공지를 고치면 나중 저장이 거부되는 대신
 * {@code HibernateException("The database returned no natively generated values")} 가 올라오고,
 * SQLSTATE 가 없어 500 으로 나갑니다. 프론트는 5xx 를 재시도 대상으로 다루므로
 * <b>낡은 본문이 재시도로 덮어써집니다.</b>
 * {@code ArticleMappingTest} 가 아니라 {@code ArticleOptimisticLockTest} 가 이 회귀를 잡습니다.
 *
 * <p>그래서 트리거가 바꾼 값({@code summary}, 카운터, {@code updated_at})은
 * <b>수정 후 메모리에서 낡습니다.</b> 응답에 최신 값이 필요하면
 * flush 후 {@code EntityManager.refresh(article)} 로 한 번 읽으세요.
 *
 * <h2>{@code @DynamicUpdate} 를 붙인 이유</h2>
 * Hibernate 는 기본적으로 <b>변경되지 않은 컬럼까지 전부</b> UPDATE 문에 싣습니다.
 * 이 테이블에는 "실제로 바뀐 컬럼" 을 보고 판단하는 트리거가 세 개 걸려 있습니다
 * ({@code IS DISTINCT FROM} 비교라 같은 값 재기록은 걸러지지만,
 * 문장이 짧을수록 의도가 드러나고 lock 경합도 줄어듭니다).
 * 특히 크롤러 강등 트리거는 {@code title/content/starts_on/ends_on} 을 보는데,
 * 관리자가 상태만 바꾼 UPDATE 에 본문이 실려 나가는 건 읽는 사람을 헷갈리게 합니다.
 */
@Getter
@Entity
@Table(name = "articles")
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseTimeEntity {

    /** {@code title varchar(500)}. 넘기면 DB 가 22001 을 던지므로 앱에서 먼저 막습니다. */
    private static final int TITLE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 생성 후 변경 불가(IN001). {@code updatable=false} 로 앱에서 시도 자체를 막습니다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private SourceType sourceType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ArticleStatus status;

    /**
     * 발행 시각. <b>DB 가 소유합니다</b>(V6 {@code trg_articles_25_published_at}).
     *
     * <p>앱 시계로 찍으면 인스턴스가 여러 대일 때 발행 순서가 뒤집히고,
     * 무엇보다 <b>앱을 거치지 않는 발행 경로</b>가 있습니다 —
     * 크롤러는 정상 공지를 검수 없이 곧바로 노출시키려고 {@code status='PUBLISHED'} 로
     * 직접 INSERT 합니다. 그 경로에도 같은 규칙이 적용되려면 DB 가 채워야 합니다.
     *
     * <p>트리거는 <b>비어 있을 때만</b> 채웁니다. 그래서 배포 취소 후 재발행해도
     * 최초 발행 시각이 유지됩니다 — 앱이 따로 신경 쓸 게 없습니다.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "published_at", insertable = false, updatable = false)
    private OffsetDateTime publishedAt;

    /**
     * 작성자. 연관관계로 매핑하지 않습니다.
     *
     * <p>{@code @ManyToOne} 이면 목록 조회마다 프록시가 생기고, 관리자 화면에서
     * 작성자 이름을 찍는 순간 N+1 이 됩니다. 이 값이 필요한 곳은 관리자 화면뿐이고
     * 거기서는 DTO projection 으로 users 를 join 해 가져오는 편이 낫습니다.
     * ({@code ON DELETE SET NULL} 이라 NULL 일 수 있습니다 = 크롤러 수집분)
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * AI 요약 캐시. <b>앱은 이 필드로 쓰지 않습니다.</b>
     *
     * <p>두 가지 이유로 읽기 전용입니다.
     * <ul>
     *   <li>저장은 {@code version}·{@code updated_at} 을 건드리지 않아야 해서 native UPDATE 로만 합니다.
     *       엔티티로 쓰면 요약이 생성될 때마다 낙관적 잠금 버전이 올라가 관리자 수정과 충돌합니다.</li>
     *   <li>무효화는 트리거({@code trg_articles_30_summary_invalidate})가 합니다.
     *       제목·본문·기간이 바뀌면 DB 가 알아서 NULL 로 만듭니다.</li>
     * </ul>
     * <p>{@code @Generated} 는 <b>INSERT 시점만</b>입니다. UPDATE 를 넣으면 안 됩니다 —
     * 이유는 아래 "낙관적 잠금과 @Generated" 절에 있습니다.
     * 수정 직후의 요약(=NULL)이 응답에 필요하면 {@code EntityManager.refresh} 로 읽으세요.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "summary", insertable = false, updatable = false, columnDefinition = "text")
    private String summary;

    /** 크롤러/AI 가 판정한 최고 유사도(0~100). NULL = 미판정. */
    @Column(name = "similarity_score", precision = 5, scale = 2)
    private BigDecimal similarityScore;

    /** 어느 공지와 비슷한지. 점수만으로는 관리자가 병합 판단을 못 합니다. */
    @Column(name = "similar_article_id")
    private Long similarArticleId;

    // ── 카운터 ────────────────────────────────────────────────────────────────
    // 전부 DB 트리거(북마크·좋아요·댓글)와 배치(조회수)가 소유합니다.
    //
    // ★ 읽기 전용으로 막지 않으면 데이터가 깨집니다.
    //   엔티티를 읽어 온 뒤 제목만 고쳐 저장하는 흔한 흐름에서, 그 사이 다른 사용자가
    //   북마크를 눌렀다면 Hibernate 가 메모리의 옛 카운터 값을 그대로 UPDATE 에 실어
    //   트리거가 올려둔 값을 덮어씁니다. @Version 은 이걸 막지 못합니다 —
    //   트리거는 version 을 올리지 않으니까요(그게 의도입니다).
    //
    //   INSERT 직후 DEFAULT 0 을 읽어 오기 위해 @Generated(INSERT) 만 붙입니다.
    //   UPDATE 뒤의 최신 값이 필요하면 refresh 로 읽습니다(아래 절 참조).

    @Generated(event = EventType.INSERT)
    @Column(name = "bookmark_count", nullable = false, insertable = false, updatable = false)
    private int bookmarkCount;

    @Generated(event = EventType.INSERT)
    @Column(name = "comment_count", nullable = false, insertable = false, updatable = false)
    private int commentCount;

    @Generated(event = EventType.INSERT)
    @Column(name = "view_count", nullable = false, insertable = false, updatable = false)
    private long viewCount;

    /**
     * 낙관적 잠금.
     *
     * <p>관리자 두 명이 같은 공지를 동시에 수정하는 것만 막습니다.
     * 카운터·조회수·요약 변경은 <b>일부러</b> version 을 올리지 않습니다.
     * 누가 북마크를 눌렀다고 관리자의 수정이 실패하면 안 되니까요.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ─────────────────────────────────────────────────────────────────────────
    // 생성
    // ─────────────────────────────────────────────────────────────────────────

    private Article(SourceType sourceType, ArticleStatus status, String title, String content,
                    LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        validateBody(title, content);
        validatePeriod(startsOn, endsOn);
        this.sourceType = sourceType;
        this.status = status;
        this.title = title;
        this.content = content;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
    }

    /**
     * 초기 상태를 지정해 만듭니다. ADM-06 관리자 작성 전용입니다.
     *
     * <p><b>전이가 아니라 생성입니다.</b> {@link #changeStatus} 를 거치지 않으므로
     * "검수 건너뛰기 금지" 규칙이 적용되지 않습니다 — 관리자가 직접 쓴 글은
     * 검수할 대상이 자기 자신이라 단계를 밟을 이유가 없습니다.
     * 크롤러도 정상 공지를 곧바로 {@code PUBLISHED} 로 넣습니다(같은 이유).
     *
     * <p>대신 <b>출처 유형에 맞는 상태인지는 확인</b>합니다.
     * DB CHECK 도 막지만 거기까지 가면 23514 로 뭉뚱그려져 어느 값이 문제인지 알 수 없습니다.
     */
    public static Article createWithStatus(SourceType sourceType, ArticleStatus initialStatus,
                                           String title, String content,
                                           LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        if (!initialStatus.isAllowedFor(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_SOURCE);
        }
        return new Article(sourceType, initialStatus, title, content, startsOn, endsOn, createdBy);
    }

    /**
     * 관리자가 직접 쓴 학교 공지.
     *
     * <p>크롤러 수집분과 같은 {@code PENDING_REVIEW} 로 시작합니다.
     * 감사 로그의 첫 줄이 출처와 무관하게 같은 모양이 되고,
     * 작성자가 곧바로 검수 완료로 올리면 되므로 단계가 실질적으로 늘지 않습니다.
     */
    public static Article createSchoolArticle(String title, String content,
                                              LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        return new Article(SourceType.SCHOOL, ArticleStatus.PENDING_REVIEW,
                title, content, startsOn, endsOn, createdBy);
    }

    /** 동아리 공지. 작성 중 상태로 시작합니다. */
    public static Article createClubArticle(String title, String content,
                                            LocalDate startsOn, LocalDate endsOn, Long createdBy) {
        return new Article(SourceType.CLUB, ArticleStatus.DRAFT,
                title, content, startsOn, endsOn, createdBy);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 본문 수정. 트리거가 보는 화이트리스트 네 컬럼을 한 번에 다룹니다.
     *
     * <p>이걸 호출하면 DB 가 {@code summary} 를 NULL 로 만들고 {@code updated_at} 을 올립니다.
     * 앱이 따로 처리할 게 없습니다 — 오히려 따로 처리하면 규칙이 두 곳에 생깁니다.
     */
    public void edit(String title, String content, LocalDate startsOn, LocalDate endsOn) {
        validateBody(title, content);
        validatePeriod(startsOn, endsOn);
        this.title = title;
        this.content = content;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    /**
     * 상태 전이. 허용 여부는 {@link ArticleStatus} 가 판단합니다.
     *
     * <p>{@code published_at} 은 DB 트리거가 <b>처음 발행할 때만</b> 찍고 이후로는 유지합니다.
     * 배포를 취소했다 다시 올렸다고 발행 시각이 바뀌면 피드 정렬이 흔들리고,
     * "신규 수집분(NULL)과 재검수 건(값 있음)" 을 구분하는 표식도 망가집니다.
     */
    public void changeStatus(ArticleStatus next) {
        if (!next.isAllowedFor(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_SOURCE);
        }
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        applyStatus(next);
    }

    /**
     * 휴지통에서 복구. 대상은 <b>감사 로그가 기억하는 직전 상태</b>여야 합니다.
     *
     * <p>{@link #changeStatus} 와 분리한 이유는 TRASHED 를 출발점으로 하는 전이를
     * 일반 경로에서 허용해 버리면 "휴지통 → 임의 상태" 가 열리기 때문입니다.
     * 직전 상태가 무엇인지는 이 엔티티가 알 수 없으므로 서비스가 조회해서 넘깁니다.
     */
    public void restoreTo(ArticleStatus previous) {
        if (status != ArticleStatus.TRASHED) {
            throw new BusinessException(ErrorCode.NOT_IN_TRASH);
        }
        if (previous == ArticleStatus.TRASHED || !previous.isAllowedFor(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_SOURCE);
        }
        applyStatus(previous);
    }

    /** 크롤러/AI 가 판정한 중복 의심 정보. 관리자 "확인 필요" 목록의 근거입니다. */
    public void markSimilarTo(BigDecimal score, Long similarArticleId) {
        if (similarArticleId != null && similarArticleId.equals(this.id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);   // ck_articles_similar_self
        }
        this.similarityScore = score;
        this.similarArticleId = similarArticleId;
    }

    /** 관리자가 병합 판단을 끝냈을 때. 다시 "확인 필요" 목록에 뜨지 않게 합니다. */
    public void clearSimilarity() {
        this.similarityScore = null;
        this.similarArticleId = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 조회 편의
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isVisibleToUsers() {
        return status.isVisibleToUsers();
    }

    /** 기간 정보가 온전한지. 캘린더 노출({@code period} 생성 컬럼) 여부와 같은 조건입니다. */
    public boolean hasCompletePeriod() {
        return startsOn != null && endsOn != null;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code published_at} 을 여기서 찍지 않습니다. DB 트리거가 합니다.
     * "최초 발행 시각 유지" 규칙도 트리거의 {@code IS NULL} 조건이 대신 지킵니다.
     *
     * <p>대신 flush 이후에도 이 필드는 메모리에서 낡습니다.
     * 응답에 발행 시각이 필요하면 {@code EntityManager.refresh} 로 읽으세요.
     */
    private void applyStatus(ArticleStatus next) {
        this.status = next;
    }

    /** 컬럼 폭({@code varchar(500)})과 NOT NULL 을 앱에서 먼저 봅니다. */
    private static void validateBody(String title, String content) {
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * {@code ck_articles_period_order} 를 앱에서 먼저 검사합니다.
     * DB 에만 맡기면 23514 가 올라와 "잘못된 입력" 이라는 뭉뚱그린 메시지가 나갑니다.
     */
    private static void validatePeriod(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn != null && endsOn != null && startsOn.isAfter(endsOn)) {
            throw new BusinessException(ErrorCode.INVALID_ARTICLE_PERIOD);
        }
    }
}
