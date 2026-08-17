-- =============================================================================
-- V2. 테이블 + CHECK + FK
--
-- ★ 코드성 컬럼은 전부 varchar + CHECK 다 (PostgreSQL native ENUM 아님).
--   - JPA는 @Enumerated(EnumType.STRING) 만으로 매핑된다 (추가 설정 불필요)
--   - ddl-auto=validate 가 타입 불일치로 부팅을 막지 않는다
--   - 감사 로그(varchar(30))와 타입이 일치한다
--   - 값 추가 시 ALTER TYPE ADD VALUE 의 트랜잭션 제약을 겪지 않는다
--   DBML의 Enum 블록은 ERD 가독성용 표기이며 실제 타입은 여기가 기준이다.
--
-- FK 의존 때문에 생성 순서가 정해져 있다:
--   vendors/categories/club_types -> users -> articles -> 나머지
-- =============================================================================


-- =============================================================================
-- 마스터
-- =============================================================================

CREATE TABLE vendors (
    id           bigserial     PRIMARY KEY,
    name         varchar(100)  NOT NULL,
    initial      varchar(100)  NOT NULL UNIQUE,
    type         varchar(20)   NOT NULL,
    homepage_url varchar(500),
    is_active    boolean       NOT NULL DEFAULT true,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_vendors_type CHECK (type IN ('SCHOOL', 'CLUB'))
);

COMMENT ON COLUMN vendors.initial IS '크롤러가 참조하는 business key. 생성 후 변경 금지';
COMMENT ON COLUMN vendors.type    IS '생성 후 변경 금지 — article_vendors 교차 검증의 전제';


CREATE TABLE categories (
    id         bigserial     PRIMARY KEY,
    code       varchar(50)   NOT NULL UNIQUE,
    name       varchar(100)  NOT NULL UNIQUE,
    is_active  boolean       NOT NULL DEFAULT true,
    sort_order int           NOT NULL DEFAULT 0,
    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON COLUMN categories.code      IS '크롤러 AI 분류 계약 키. 생성 후 변경 금지';
COMMENT ON COLUMN categories.is_active IS 'false = 신규 선택/분류에서 숨김. 기존 관계는 보존';


CREATE TABLE club_types (
    id         bigserial     PRIMARY KEY,
    code       varchar(50)   NOT NULL UNIQUE,
    name       varchar(100)  NOT NULL UNIQUE,
    is_active  boolean       NOT NULL DEFAULT true,
    sort_order int           NOT NULL DEFAULT 0,
    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON TABLE club_types IS '동아리 추천 taxonomy. 항목은 seed 데이터로 관리';


-- =============================================================================
-- 사용자
-- =============================================================================

CREATE TABLE users (
    id                         bigserial     PRIMARY KEY,
    email                      varchar(255)  NOT NULL,
    name                       varchar(50),
    major_vendor_id            bigint,
    role                       varchar(20)   NOT NULL DEFAULT 'USER',
    status                     varchar(20)   NOT NULL DEFAULT 'ACTIVE',
    email_notification_enabled boolean       NOT NULL DEFAULT true,
    withdrawn_at               timestamptz,
    onboarding_completed_at    timestamptz,
    created_at                 timestamptz   NOT NULL DEFAULT now(),
    updated_at                 timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_role   CHECK (role   IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    -- 소문자 정규화를 앱에만 맡기면 Kim@inha.ac.kr 과 kim@inha.ac.kr 이 별개 계정이 된다.
    -- 아래 partial unique 가 대소문자를 구분하므로 DB가 막지 못한다.
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_withdrawn   CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)),

    CONSTRAINT fk_users_major_vendor FOREIGN KEY (major_vendor_id)
        REFERENCES vendors (id) ON DELETE SET NULL
);

COMMENT ON COLUMN users.onboarding_completed_at IS 'NULL = 온보딩 미완료';


CREATE TABLE user_interest_categories (
    user_id     bigint      NOT NULL,
    category_id bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_interest_categories PRIMARY KEY (user_id, category_id),
    CONSTRAINT fk_uic_user     FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
    CONSTRAINT fk_uic_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
);


CREATE TABLE user_club_type_interests (
    user_id      bigint      NOT NULL,
    club_type_id bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_club_type_interests PRIMARY KEY (user_id, club_type_id),
    CONSTRAINT fk_ucti_user      FOREIGN KEY (user_id)      REFERENCES users (id)      ON DELETE CASCADE,
    CONSTRAINT fk_ucti_club_type FOREIGN KEY (club_type_id) REFERENCES club_types (id) ON DELETE RESTRICT
);


CREATE TABLE vendor_club_types (
    vendor_id    bigint      NOT NULL,
    club_type_id bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_vendor_club_types PRIMARY KEY (vendor_id, club_type_id),
    CONSTRAINT fk_vct_vendor    FOREIGN KEY (vendor_id)    REFERENCES vendors (id)    ON DELETE CASCADE,
    CONSTRAINT fk_vct_club_type FOREIGN KEY (club_type_id) REFERENCES club_types (id) ON DELETE RESTRICT
);

COMMENT ON TABLE vendor_club_types IS
    'CLUB vendor 태깅. 추천 score = 사용자와 vendor 가 공유하는 active club_type 수';


-- =============================================================================
-- 공지
-- =============================================================================

CREATE TABLE articles (
    id             bigserial     PRIMARY KEY,
    source_type    varchar(20)   NOT NULL,
    title          varchar(500)  NOT NULL,
    content        text          NOT NULL,

    -- 태그/엔티티 제거 후 소문자화. pg_bigm GIN 인덱스 대상.
    -- 함수 정의 변경 시 이 컬럼 재생성 필요(V1 주석 참조).
    search_text    text          GENERATED ALWAYS AS
                                 (inform_normalize_search(title, content)) STORED,

    starts_on      date,
    ends_on        date,

    -- ★ CASE 가드 필수.
    --   daterange(NULL, '2026-08-31', '[]') 는 NULL 이 아니라 하한 무한대 범위가 된다.
    --   가드가 없으면 "마감일만 있는 공지"가 캘린더의 모든 달에 나타난다.
    period         daterange     GENERATED ALWAYS AS (
                                     CASE
                                         WHEN starts_on IS NOT NULL AND ends_on IS NOT NULL
                                         THEN daterange(starts_on, ends_on, '[]')
                                     END
                                 ) STORED,

    status         varchar(30)   NOT NULL,
    published_at   timestamptz,
    created_by     bigint,
    summary        text,

    bookmark_count int           NOT NULL DEFAULT 0,
    like_count     int           NOT NULL DEFAULT 0,
    comment_count  int           NOT NULL DEFAULT 0,
    view_count     bigint        NOT NULL DEFAULT 0,

    version        bigint        NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_articles_source_type CHECK (source_type IN ('SCHOOL', 'CLUB')),

    -- 상태 "집합"은 DB가 막는다. 상태 "전이"(A->B 허용 여부)는 앱 책임이다.
    CONSTRAINT ck_articles_status_by_source CHECK (
        (source_type = 'SCHOOL' AND status IN
            ('PENDING_REVIEW', 'DUPLICATE_SUSPECTED', 'READY_TO_PUBLISH', 'PUBLISHED', 'TRASHED'))
        OR
        (source_type = 'CLUB'   AND status IN
            ('DRAFT', 'PUBLISHED', 'TRASHED'))
    ),

    CONSTRAINT ck_articles_period_order CHECK (
        starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on
    ),
    CONSTRAINT ck_articles_published CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_articles_counters CHECK (
        bookmark_count >= 0 AND like_count >= 0 AND comment_count >= 0 AND view_count >= 0
    ),
    CONSTRAINT ck_articles_version CHECK (version >= 0),

    CONSTRAINT fk_articles_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL
);

COMMENT ON COLUMN articles.summary IS
    'AI 요약 캐시. 앱은 native UPDATE 로만 쓴다(version/updated_at 미변경)';
COMMENT ON COLUMN articles.view_count IS
    'Redis delta 를 배치로 합산하는 durable counter. 재계산 원천이 없다';
COMMENT ON COLUMN articles.version IS
    'JPA @Version. 카운터/조회수/요약 변경은 version 을 올리지 않는다';


CREATE TABLE article_vendors (
    id           bigserial     PRIMARY KEY,
    article_id   bigint        NOT NULL,
    vendor_id    bigint        NOT NULL,
    source_url   varchar(1000),
    external_key varchar(255),

    CONSTRAINT fk_av_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_av_vendor  FOREIGN KEY (vendor_id)  REFERENCES vendors (id)  ON DELETE RESTRICT
);

COMMENT ON TABLE article_vendors IS
    '행 하나 = 원본 게시물 하나. (article_id, vendor_id) 유니크를 의도적으로 걸지 않는다 — 같은 게시판 재게시를 모두 보존해야 재수집 루프가 안 생긴다';


CREATE TABLE article_categories (
    article_id  bigint NOT NULL,
    category_id bigint NOT NULL,

    CONSTRAINT pk_article_categories PRIMARY KEY (article_id, category_id),
    CONSTRAINT fk_ac_article  FOREIGN KEY (article_id)  REFERENCES articles (id)   ON DELETE CASCADE,
    CONSTRAINT fk_ac_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
);


CREATE TABLE attachments (
    id            bigserial     PRIMARY KEY,
    article_id    bigint        NOT NULL,
    file_url      varchar(1000) NOT NULL,
    storage_type  varchar(20)   NOT NULL,
    object_key    varchar(500),
    original_name varchar(255),
    content_type  varchar(100),
    size_bytes    bigint,
    sort_order    int           NOT NULL DEFAULT 0,
    created_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_attachments_storage_type CHECK (storage_type IN ('S3', 'EXTERNAL')),
    CONSTRAINT ck_attachments_object_key   CHECK ((storage_type = 'S3') = (object_key IS NOT NULL)),
    CONSTRAINT ck_attachments_size         CHECK (size_bytes IS NULL OR size_bytes > 0),

    CONSTRAINT fk_attachments_article FOREIGN KEY (article_id)
        REFERENCES articles (id) ON DELETE CASCADE
);

COMMENT ON TABLE attachments IS
    'storage ownership 은 article.source_type 과 독립적이다. 관리자는 SCHOOL 공지에도 S3 첨부를 붙일 수 있다';


-- =============================================================================
-- 사용자 활동
-- =============================================================================

CREATE TABLE bookmarks (
    user_id    bigint      NOT NULL,
    article_id bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_bookmarks PRIMARY KEY (user_id, article_id),
    CONSTRAINT fk_bookmarks_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_bookmarks_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE
);


CREATE TABLE article_likes (
    user_id    bigint      NOT NULL,
    article_id bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_article_likes PRIMARY KEY (user_id, article_id),
    CONSTRAINT fk_likes_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_likes_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE
);


CREATE TABLE comments (
    id         bigserial   PRIMARY KEY,
    article_id bigint      NOT NULL,
    user_id    bigint      NOT NULL,
    parent_id  bigint,
    content    text        NOT NULL,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_comments_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_id)  REFERENCES comments (id) ON DELETE CASCADE
);

COMMENT ON COLUMN comments.deleted_at IS
    'soft delete. 답글이 있으면 자리 유지, 없으면 하드 삭제(앱 정책)';


CREATE TABLE notifications (
    id         bigserial    PRIMARY KEY,
    user_id    bigint       NOT NULL,
    article_id bigint,
    type       varchar(30)  NOT NULL,
    dedup_key  varchar(50)  NOT NULL DEFAULT '',
    title      varchar(255) NOT NULL,
    message    text         NOT NULL,
    read_at    timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_notifications_type CHECK (type IN ('DEADLINE_D1', 'COMMENT_REPLY')),

    CONSTRAINT fk_notifications_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_notifications_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE
);

COMMENT ON COLUMN notifications.dedup_key IS
    'DEADLINE_D1 = 마감일(YYYY-MM-DD) / COMMENT_REPLY = 답글 comment id';


-- =============================================================================
-- 서비스 공지
-- =============================================================================

CREATE TABLE announcements (
    id           bigserial     PRIMARY KEY,
    type         varchar(20)   NOT NULL,
    title        varchar(500)  NOT NULL,
    content      text          NOT NULL,
    status       varchar(20)   NOT NULL,
    is_pinned    boolean       NOT NULL DEFAULT false,
    published_at timestamptz,
    starts_on    date,
    ends_on      date,
    created_by   bigint,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_ann_type   CHECK (type   IN ('MAINTENANCE', 'UPDATE', 'EVENT', 'GENERAL')),
    CONSTRAINT ck_ann_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_ann_period CHECK (starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on),
    CONSTRAINT ck_ann_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),

    CONSTRAINT fk_ann_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL
);


-- =============================================================================
-- 감사 / 운영
-- =============================================================================

CREATE TABLE article_status_logs (
    id          bigserial    PRIMARY KEY,
    article_id  bigint       NOT NULL,
    from_status varchar(30),
    to_status   varchar(30)  NOT NULL,
    changed_by  bigint,
    memo        varchar(500),
    created_at  timestamptz  NOT NULL DEFAULT now(),

    -- ★ 허용 집합은 append-only 다.
    --   상태가 폐기되어도 과거 이력에 존재한 값은 이 목록에서 빼지 않는다.
    --   빼면 CHECK 가 과거 이력의 존재를 부정하게 되고 재검증이 불가능해진다.
    CONSTRAINT ck_asl_from CHECK (
        from_status IS NULL OR from_status IN
        ('DRAFT', 'PENDING_REVIEW', 'DUPLICATE_SUSPECTED', 'READY_TO_PUBLISH', 'PUBLISHED', 'TRASHED')
    ),
    CONSTRAINT ck_asl_to CHECK (
        to_status IN
        ('DRAFT', 'PENDING_REVIEW', 'DUPLICATE_SUSPECTED', 'READY_TO_PUBLISH', 'PUBLISHED', 'TRASHED')
    ),

    CONSTRAINT fk_asl_article    FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_asl_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)    ON DELETE SET NULL
);

COMMENT ON COLUMN article_status_logs.changed_by IS 'NULL = 크롤러/스케줄러';


CREATE TABLE user_role_logs (
    id         bigserial   PRIMARY KEY,
    user_id    bigint      NOT NULL,
    from_role  varchar(20) NOT NULL,
    to_role    varchar(20) NOT NULL,
    changed_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_url_from CHECK (from_role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_url_to   CHECK (to_role   IN ('USER', 'ADMIN')),

    CONSTRAINT fk_url_user       FOREIGN KEY (user_id)    REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_url_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
);


-- 조회수 flush 멱등성 보장용.
--
-- Redis delta 를 DB에 합산한 뒤 Redis 키를 지우는 순서인데,
-- 합산 COMMIT 과 키 삭제 사이에 프로세스가 죽으면 Redis 에 배치가 남는다.
-- 그때 "아직 안 더한 것"인지 "더했는데 못 지운 것"인지 구별할 방법이 없다.
-- 재처리하면 2배, 버리면 유실인데 view_count 는 재계산 원천이 없어 영구 손상이다.
--
-- 합산과 같은 트랜잭션에서 batch_id 를 INSERT ... ON CONFLICT DO NOTHING 하고
-- 삽입 행이 0이면 이미 반영된 배치이므로 건너뛴다. Redis 키 삭제는 순수 정리 작업이 된다.
CREATE TABLE view_count_flush_log (
    batch_id   varchar(64) PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);


CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);

COMMENT ON TABLE shedlock IS
    'ShedLock 표준 테이블. JdbcTemplateLockProvider 는 usingDbTime() 으로 구성할 것';
