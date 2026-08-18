-- =============================================================================
-- V4. 인덱스
--
-- ★ 정렬 방향(DESC)과 PARTIAL 조건이 중요하다. DBML 로는 표현할 수 없어
--   여기가 유일한 기준이다.
--
-- ★ 목록/피드 인덱스에는 예외 없이 id 를 tie-breaker 로 넣는다.
--   published_at 은 유일하지 않다 — now() 는 트랜잭션 안에서 고정값이므로
--   관리자가 30건을 일괄 배포하면 30건 전부 동일한 published_at 을 갖는다.
--   정렬 키가 유일하지 않으면 offset 페이징은 경계가 비결정적이고
--   keyset 커서는 아예 만들 수 없다. 커서 형태는 프론트 계약에 박히므로
--   나중에 바꾸면 클라이언트까지 뒤집힌다.
-- =============================================================================


-- =============================================================================
-- 마스터
-- =============================================================================

CREATE INDEX idx_vendors_type ON vendors (type, is_active);

-- categories / club_types 는 행이 수십 개라 seq scan 이 더 빠르다. 인덱스 없음.


-- =============================================================================
-- users
-- =============================================================================

-- 활성 사용자끼리만 이메일 유니크. 탈퇴자가 있어도 재가입이 된다.
CREATE UNIQUE INDEX uk_users_active_email ON users (email) WHERE status = 'ACTIVE';

-- 탈퇴 30일 경과 개인정보 마스킹 배치용. WITHDRAWN 비율이 낮아 매우 작다.
CREATE INDEX idx_users_purge ON users (withdrawn_at) WHERE status = 'WITHDRAWN';


-- =============================================================================
-- 개인화 / 추천
-- =============================================================================

-- user_vendors 는 PK(user_id, vendor_id) 만 둔다.
-- "내가 구독한 학과 목록"이 유일한 조회 방향이고, 역방향("이 학과를 구독한 사용자")은
-- 요구사항에 없다. vendor 삭제는 article_vendors 의 RESTRICT 때문에 사실상 발생하지 않는다.

-- 추천 쿼리 방향: user_club_type_interests(user_id, PK) -> vendor_club_types(club_type_id)
-- 아래 인덱스가 두 번째 단계를 index-only scan 으로 처리한다.
CREATE INDEX idx_vendor_club_types_recommend ON vendor_club_types (club_type_id, vendor_id);

-- 역방향("이 유형에 관심 있는 사용자 목록")은 요구사항에 없어 인덱스를 두지 않는다.
-- categories/club_types 삭제 시 RESTRICT 검사는 행 수가 적어 seq scan 으로 충분하다.


-- =============================================================================
-- articles
-- =============================================================================

-- 최신순 피드. status 를 PARTIAL 조건으로 빼서 인덱스를 작게 유지한다.
CREATE INDEX idx_articles_feed_all ON articles (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';

-- 출처 필터가 붙은 피드
CREATE INDEX idx_articles_feed ON articles (source_type, published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';

-- 검수 큐. 재검수 건은 published_at IS NOT NULL 로 구분한다.
CREATE INDEX idx_articles_review ON articles (status, created_at DESC, id DESC)
    WHERE status IN ('PENDING_REVIEW', 'READY_TO_PUBLISH');

-- "확인 필요 게시글" — 중복 의심분.
-- 임계값은 애플리케이션 설정이므로 인덱스 조건에는 넣지 않는다.
CREATE INDEX idx_articles_similarity ON articles (similarity_score DESC, id DESC)
    WHERE status = 'PENDING_REVIEW' AND similarity_score IS NOT NULL;

-- 결측 판정(날짜/본문/카테고리/출처)은 컬럼이나 인덱스를 두지 않는다.
-- 미검수 글은 소수라 idx_articles_review 로 좁힌 뒤 필터링해도 충분하다.

-- 인기순 / 추천순. 카운터는 0~5 에 몰려 동점이 많으므로 id tie-breaker 가 필수다.
CREATE INDEX idx_articles_popular ON articles (bookmark_count DESC, id DESC)
    WHERE status = 'PUBLISHED';
CREATE INDEX idx_articles_liked ON articles (like_count DESC, id DESC)
    WHERE status = 'PUBLISHED';

-- 캘린더 기간 겹침 (period && daterange(:from, :to, '[]'))
CREATE INDEX idx_articles_period_gist ON articles USING GIST (period)
    WHERE period IS NOT NULL;

-- ★ 마감 D-1 알림 전용.
--   한쪽 날짜만 있는 공지는 period=NULL 이라 GiST 인덱스로 찾을 수 없는데,
--   마감 알림이 가장 필요한 "○○까지 신청" 류가 정확히 그 형태다.
--   그래서 period 가 아니라 ends_on 을 직접 조회한다.
--     WHERE status='PUBLISHED' AND ends_on = CURRENT_DATE + 1
CREATE INDEX idx_articles_deadline ON articles (ends_on)
    WHERE status = 'PUBLISHED' AND ends_on IS NOT NULL;

-- 관리자 임시저장 목록. SCHOOL 수집분은 created_by 가 NULL 이라 매우 작다.
CREATE INDEX idx_articles_created_by ON articles (created_by, status)
    WHERE created_by IS NOT NULL;

-- 한글 부분일치 검색.
-- pg_trgm(3-gram)은 2글자 패턴에서 인덱스를 못 쓴다. 한국어 검색어는 2글자가
-- 압도적이라(장학/취업/인턴/학사) pg_bigm(2-gram)을 쓴다.
--     WHERE search_text LIKE likequery(lower(:keyword))
CREATE INDEX idx_articles_search_bigm ON articles USING GIN (search_text gin_bigm_ops);

-- ★ view_count 정렬 인덱스는 만들지 않는다.
--   조회순 기능이 없고, 카운터 컬럼을 인덱싱하면 flush 마다 HOT update 가 깨진다.

-- 인기/추천 인덱스가 카운터 갱신으로 자주 바뀌므로 HOT update 여지를 확보한다.
ALTER TABLE articles SET (fillfactor = 85);


-- =============================================================================
-- article_vendors
-- =============================================================================

-- 중복 수집 최종 방어선. 크롤러는 ON CONFLICT 로 이 제약을 활용한다.
CREATE UNIQUE INDEX uk_vendor_external ON article_vendors (vendor_id, external_key);

CREATE INDEX idx_av_article ON article_vendors (article_id);

-- 관리자 수기 등록분만 (article, vendor) 쌍 중복을 막는다.
-- 크롤링분은 재게시를 모두 보존해야 하므로 대상이 아니다.
CREATE UNIQUE INDEX uk_article_vendor_manual ON article_vendors (article_id, vendor_id)
    WHERE external_key IS NULL;

-- (vendor_id) 단독 인덱스는 두지 않는다 — uk_vendor_external 의 선행 컬럼과 같아 중복이다.


-- =============================================================================
-- article_categories
-- =============================================================================

-- article_id 를 붙여 index-only scan 을 유도한다.
-- 카테고리 필터는 "이 카테고리의 article_id 목록"만 필요하다.
CREATE INDEX idx_ac_category ON article_categories (category_id, article_id);


-- =============================================================================
-- attachments
-- =============================================================================

-- ★ file_url 원문이 아니라 md5 로 유니크를 잡는다.
--   varchar(1000) 전체를 btree 키에 넣으면 최대 키 크기(2704B)를 넘길 수 있다.
--   학교 사이트 다운로드 URL 은 쿼리스트링이 길고 한글 파라미터가 섞인다.
--   넘기면 크롤링 트랜잭션 전체가 롤백된다.
--   앱/크롤러의 ON CONFLICT 절도 (article_id, md5(file_url)) 로 맞출 것.
CREATE UNIQUE INDEX uk_attachments_article_file
    ON attachments (article_id, md5(file_url));

-- S3 오브젝트는 첨부 한 행이 독점 소유한다. 삭제를 idempotent 하게 만드는 전제다.
CREATE UNIQUE INDEX uk_attachments_s3_object ON attachments (object_key)
    WHERE storage_type = 'S3';

CREATE INDEX idx_attachments_article ON attachments (article_id, sort_order);


-- =============================================================================
-- 사용자 활동
-- =============================================================================

CREATE INDEX idx_bookmarks_article      ON bookmarks (article_id);
CREATE INDEX idx_bookmarks_user_created ON bookmarks (user_id, created_at DESC);

CREATE INDEX idx_likes_article ON article_likes (article_id);
-- (user_id, created_at) 은 "내가 좋아요한 글 목록" 기능이 없어 두지 않는다.

CREATE INDEX idx_comments_article ON comments (article_id, created_at, id);
CREATE INDEX idx_comments_parent  ON comments (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_comments_user    ON comments (user_id, created_at DESC, id DESC);


-- =============================================================================
-- notifications
-- =============================================================================

-- 공지 연결 알림만 중복 발송을 막는다.
-- 마감일이 연장되면 dedup_key(마감일)가 바뀌어 자연히 재발송된다.
CREATE UNIQUE INDEX uk_notifications_dedup
    ON notifications (user_id, article_id, type, dedup_key)
    WHERE article_id IS NOT NULL;

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC, id DESC);

-- 배지(안읽음 개수) 전용
CREATE INDEX idx_notifications_unread ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;

-- 부모 삭제 CASCADE 보조
CREATE INDEX idx_notifications_article ON notifications (article_id)
    WHERE article_id IS NOT NULL;

-- 30일 경과 알림 정리 배치용. 알림은 사용자 x 공지로 곱해져 가장 빨리 커진다.
CREATE INDEX idx_notifications_purge ON notifications (created_at);


-- =============================================================================
-- announcements
-- =============================================================================

CREATE INDEX idx_ann_feed ON announcements (is_pinned DESC, published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';


-- =============================================================================
-- 감사 로그
-- =============================================================================

-- id 는 사슬 검증의 tie-breaker 이자 retention purge 의 판정 기준이다.
CREATE INDEX idx_asl_article    ON article_status_logs (article_id, created_at, id);
CREATE INDEX idx_asl_changed_by ON article_status_logs (changed_by, created_at)
    WHERE changed_by IS NOT NULL;
CREATE INDEX idx_asl_retention  ON article_status_logs (created_at);

CREATE INDEX idx_url_user       ON user_role_logs (user_id, created_at);
CREATE INDEX idx_url_changed_by ON user_role_logs (changed_by, created_at)
    WHERE changed_by IS NOT NULL;
CREATE INDEX idx_url_retention  ON user_role_logs (created_at);
