-- =============================================================================
-- V3. 트리거 함수 + 트리거
--
-- ★ 사용자 정의 SQLSTATE 규약
--   plpgsql 의 맨 RAISE EXCEPTION 은 SQLSTATE 가 전부 P0001 로 뭉개진다.
--   그러면 Spring 이 UncategorizedSQLException 으로 올려 클라이언트에 500 이 나가고,
--   "답글의 답글 금지" 같은 400 이어야 할 검증이 서버 장애로 보인다.
--   프론트는 5xx 를 재시도 대상으로 다루므로 잘못된 입력을 무한 재시도한다.
--
--   그래서 규칙마다 고유 SQLSTATE 를 부여한다.
--   GlobalExceptionHandler 에 DataAccessException 핸들러를 두고
--   SQLException.getSQLState() 로 분기해 ErrorCode 로 매핑할 것.
--
--     IN001  불변 컬럼 변경 시도
--     IN002  article.source_type 과 vendor.type 불일치
--     IN003  크롤러가 기록한 출처인데 external_key / source_url 누락
--     IN004  답글의 답글 (depth 위반)
--     IN005  다른 공지의 댓글을 parent 로 지정
--     IN006  major_vendor 가 SCHOOL 이 아님
--     IN007  크롤러가 EXTERNAL 이 아닌 첨부를 기록하려 함
--     IN008  비활성 club_type 을 신규 선택/태깅
--     IN009  CLUB 이 아닌 vendor 에 club_type 태깅
--
-- ★ 트리거 이름의 숫자 접두사는 실행 순서다.
--   PostgreSQL 은 같은 시점의 트리거를 이름 알파벳 순으로 실행한다.
--   articles 의 updated_at 판정은 크롤러 강등이 status 를 바꾼 뒤여야 하므로
--   순서를 이름에 명시해 두었다. 이름을 바꾸면 조용히 깨진다.
-- =============================================================================


-- =============================================================================
-- 1. updated_at (공용)
-- =============================================================================

CREATE TRIGGER trg_vendors_updated_at       BEFORE UPDATE ON vendors
    FOR EACH ROW EXECUTE FUNCTION inform_set_updated_at();
CREATE TRIGGER trg_categories_updated_at    BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION inform_set_updated_at();
CREATE TRIGGER trg_club_types_updated_at    BEFORE UPDATE ON club_types
    FOR EACH ROW EXECUTE FUNCTION inform_set_updated_at();
CREATE TRIGGER trg_users_updated_at         BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION inform_set_updated_at();
CREATE TRIGGER trg_comments_updated_at      BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION inform_set_updated_at();
CREATE TRIGGER trg_announcements_updated_at BEFORE UPDATE ON announcements
    FOR EACH ROW EXECUTE FUNCTION inform_set_updated_at();


-- =============================================================================
-- 2. 불변 컬럼
-- =============================================================================

CREATE OR REPLACE FUNCTION inform_vendors_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.type IS DISTINCT FROM OLD.type THEN
        RAISE EXCEPTION 'vendors.type 은 생성 후 변경할 수 없습니다 (id=%)', OLD.id
            USING ERRCODE = 'IN001';
    END IF;
    IF NEW.initial IS DISTINCT FROM OLD.initial THEN
        RAISE EXCEPTION 'vendors.initial 은 크롤러 business key 이므로 변경할 수 없습니다 (id=%)', OLD.id
            USING ERRCODE = 'IN001';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_vendors_10_immutable BEFORE UPDATE ON vendors
    FOR EACH ROW EXECUTE FUNCTION inform_vendors_immutable();


CREATE OR REPLACE FUNCTION inform_code_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.code IS DISTINCT FROM OLD.code THEN
        RAISE EXCEPTION '%.code 는 연동 계약 키이므로 변경할 수 없습니다 (id=%)', TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'IN001';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_categories_10_immutable BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION inform_code_immutable();
CREATE TRIGGER trg_club_types_10_immutable BEFORE UPDATE ON club_types
    FOR EACH ROW EXECUTE FUNCTION inform_code_immutable();


-- =============================================================================
-- 3. users
-- =============================================================================

-- major_vendor 는 SCHOOL 만.
-- vendors.type 이 불변이므로 이 교차 검증은 소급으로 깨지지 않는다.
CREATE OR REPLACE FUNCTION inform_users_major_vendor_check()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_type varchar(20);
BEGIN
    IF NEW.major_vendor_id IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT type INTO v_type FROM vendors WHERE id = NEW.major_vendor_id;
    IF v_type <> 'SCHOOL' THEN
        RAISE EXCEPTION '전공은 학과(SCHOOL)만 선택할 수 있습니다 (vendor_id=%, type=%)',
                        NEW.major_vendor_id, v_type
            USING ERRCODE = 'IN006';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_users_20_major_vendor
    BEFORE INSERT OR UPDATE OF major_vendor_id ON users
    FOR EACH ROW EXECUTE FUNCTION inform_users_major_vendor_check();


-- 역할 변경 감사. 앱이 로그를 빠뜨릴 방법이 없도록 트리거가 같은 트랜잭션에서 기록한다.
-- ★ SECURITY DEFINER 필수. 아래 status 감사 함수의 주석 참조.
CREATE OR REPLACE FUNCTION inform_users_role_audit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role THEN
        INSERT INTO user_role_logs (user_id, from_role, to_role, changed_by)
        VALUES (NEW.id, OLD.role, NEW.role, inform_current_actor());
    END IF;
    RETURN NULL;
END $$;

-- status 감사와 같은 이유로 컬럼을 좁히지 않는다.
-- 지금은 role 을 고치는 트리거가 없지만, 나중에 하나 생기면 조용히 누락된다.
CREATE TRIGGER trg_users_90_role_audit
    AFTER UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION inform_users_role_audit();


-- =============================================================================
-- 4. articles
-- =============================================================================

-- 4-1. source_type 불변 (article_vendors / attachments 교차 검증의 전제)
CREATE OR REPLACE FUNCTION inform_articles_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.source_type IS DISTINCT FROM OLD.source_type THEN
        RAISE EXCEPTION 'articles.source_type 은 생성 후 변경할 수 없습니다 (id=%)', OLD.id
            USING ERRCODE = 'IN001';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_articles_10_immutable BEFORE UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION inform_articles_immutable();


-- 4-2. 크롤러 재검수 강등 (D9)
--
-- 크롤러가 SCHOOL 공지의 본문/기간을 바꾸면 검수 대기로 되돌린다.
-- 이미 휴지통에 있는 건 관리자의 명시적 결정이므로 되살리지 않는다.
--
-- ★ 판별은 session_user 다. SET ROLE 을 써도 원래 로그인 롤이 유지된다.
-- ★ IS DISTINCT FROM 으로 실제 변경만 잡는다.
--   no-op UPDATE 에 반응하면 크롤링 주기마다 전체 공지가 검수 큐로 쏟아진다.
CREATE OR REPLACE FUNCTION inform_articles_crawler_policy()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF session_user <> 'inform_crawler' OR NEW.source_type <> 'SCHOOL' THEN
        RETURN NEW;
    END IF;

    IF (OLD.title, OLD.content, OLD.starts_on, OLD.ends_on)
       IS DISTINCT FROM
       (NEW.title, NEW.content, NEW.starts_on, NEW.ends_on)
    THEN
        IF OLD.status <> 'TRASHED' THEN
            NEW.status := 'PENDING_REVIEW';
        END IF;
        -- published_at 은 유지한다. 신규 수집분(NULL)과 재검수 건을 구분하는 표식이다.
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_articles_20_crawler_policy BEFORE UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION inform_articles_crawler_policy();


-- 4-3. AI 요약 무효화. 수정 주체와 무관하게 이 트리거만 책임진다.
CREATE OR REPLACE FUNCTION inform_articles_summary_invalidate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (OLD.title, OLD.content, OLD.starts_on, OLD.ends_on)
       IS DISTINCT FROM
       (NEW.title, NEW.content, NEW.starts_on, NEW.ends_on)
    THEN
        NEW.summary := NULL;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_articles_30_summary_invalidate BEFORE UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION inform_articles_summary_invalidate();


-- 4-4. updated_at — 화이트리스트 방식
--
-- "제외 목록"이 아니라 "포함 목록"으로 판정한다.
-- 제외 방식으로 하면 카운터·조회수·요약이 늘어날 때마다 목록을 넓혀야 하고,
-- 하나 빠뜨리면 "누군가 상세 페이지를 열었을 뿐인데 수정 시각이 바뀌는" 버그가 난다.
--
-- ★ 반드시 다른 BEFORE 트리거 뒤에 실행되어야 한다 (4-2 가 status 를 바꾸므로).
--   이름의 40 이 그 순서를 보장한다.
CREATE OR REPLACE FUNCTION inform_articles_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (OLD.title, OLD.content, OLD.starts_on, OLD.ends_on,
        OLD.status, OLD.published_at, OLD.created_by)
       IS DISTINCT FROM
       (NEW.title, NEW.content, NEW.starts_on, NEW.ends_on,
        NEW.status, NEW.published_at, NEW.created_by)
    THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_articles_40_updated_at BEFORE UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION inform_articles_updated_at();


-- 4-5. 상태 변경 감사
--
-- 크롤러 강등도 앱을 거치지 않으므로 여기서 함께 기록된다.
-- 이게 없으면 관리자 이력 화면에 구멍이 생기고 상태 사슬 검증이 오탐한다.
--
-- memo 는 트리거가 알 수 없으므로 트랜잭션-로컬 GUC 로 받는다.
--     SELECT set_config('app.status_change_memo', :memo, true);
-- ★★ SECURITY DEFINER 필수.
--   plpgsql 트리거 함수는 기본이 SECURITY INVOKER 이므로 호출한 롤의 권한으로 실행된다.
--   inform_crawler 는 article_status_logs 에 INSERT 권한이 없으므로 그대로 두면
--   "permission denied for table article_status_logs" 로 크롤러의 INSERT/UPDATE 가
--   전부 실패한다. (실제로 통합 테스트에서 크롤러 UPDATE 가 통째로 롤백되는 것을 확인했다)
--
--   해법으로 크롤러에게 INSERT 권한을 주면 감사 로그를 위조할 수 있으므로 안 된다.
--   SECURITY DEFINER 로 함수 소유자(inform) 권한으로 실행하면
--   크롤러는 로그를 직접 쓸 수 없으면서 트리거를 통한 기록은 남는다.
--
--   SECURITY DEFINER 함수는 search_path 를 반드시 고정한다 (탐색 경로 가로채기 방지).
CREATE OR REPLACE FUNCTION inform_articles_status_audit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO article_status_logs (article_id, from_status, to_status, changed_by, memo)
        VALUES (NEW.id, NULL, NEW.status, inform_current_actor(), inform_current_memo());
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO article_status_logs (article_id, from_status, to_status, changed_by, memo)
        VALUES (NEW.id, OLD.status, NEW.status, inform_current_actor(), inform_current_memo());
    END IF;
    RETURN NULL;
END $$;

-- ★★ 반드시 "UPDATE" 전체여야 한다. "UPDATE OF status" 로 좁히면 안 된다.
--   UPDATE OF <col> 은 UPDATE 문의 SET 목록에 그 컬럼이 있을 때만 발동한다.
--   크롤러의 UPDATE 는 SET content=..., version=... 이고 status 는
--   trg_articles_20_crawler_policy 가 NEW 를 고쳐서 바뀐다. 문장에는 없다.
--   따라서 OF status 로 좁히면 D9 강등이 감사 로그에 전혀 남지 않는다.
--   (실제로 이 조건 때문에 강등 로그가 누락되는 것을 통합 테스트로 확인했다)
--   함수 안에서 IS DISTINCT FROM 으로 판정하므로 넓게 걸어도 불필요한 INSERT 는 없다.
CREATE TRIGGER trg_articles_90_status_audit
    AFTER INSERT OR UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION inform_articles_status_audit();


-- =============================================================================
-- 5. article_vendors — 출처 무결성
-- =============================================================================

CREATE OR REPLACE FUNCTION inform_article_vendor_integrity()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_source_type varchar(20);
    v_vendor_type varchar(20);
BEGIN
    SELECT source_type INTO v_source_type FROM articles WHERE id = NEW.article_id;
    SELECT type        INTO v_vendor_type FROM vendors  WHERE id = NEW.vendor_id;

    IF v_source_type <> v_vendor_type THEN
        RAISE EXCEPTION '공지 출처(%)와 제공처 유형(%)이 다릅니다', v_source_type, v_vendor_type
            USING ERRCODE = 'IN002';
    END IF;

    -- ★ external_key 필수 조건은 "SCHOOL 공지"가 아니라 "크롤러가 쓴 행"이 기준이다.
    --   관리자는 화면에서 학과와 URL 만 입력하고 원본 게시판의 글 번호는 알 수 없다.
    --   source_type 기준으로 걸면 관리자의 출처 수기 추가가 전부 IN003 으로 막힌다.
    IF session_user = 'inform_crawler'
       AND (NEW.external_key IS NULL OR NEW.source_url IS NULL) THEN
        RAISE EXCEPTION '크롤러가 기록하는 출처는 external_key 와 source_url 이 필수입니다'
            USING ERRCODE = 'IN003';
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_av_10_integrity BEFORE INSERT OR UPDATE ON article_vendors
    FOR EACH ROW EXECUTE FUNCTION inform_article_vendor_integrity();


-- =============================================================================
-- 6. attachments — 작성 주체 정책
--
-- article.source_type 과의 교차 강제는 하지 않는다(관리자는 SCHOOL 공지에도
-- S3 이미지를 붙일 수 있어야 한다). 대신 "크롤러는 EXTERNAL 만"은 막는다.
-- 버그 있는 크롤러가 존재하지 않는 S3 키를 남기면 삭제 로직이 헛돈다.
-- =============================================================================

CREATE OR REPLACE FUNCTION inform_attachments_writer_policy()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF session_user = 'inform_crawler' AND NEW.storage_type <> 'EXTERNAL' THEN
        RAISE EXCEPTION '크롤러는 EXTERNAL 첨부만 기록할 수 있습니다 (요청=%)', NEW.storage_type
            USING ERRCODE = 'IN007';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_attachments_10_writer_policy
    BEFORE INSERT OR UPDATE ON attachments
    FOR EACH ROW EXECUTE FUNCTION inform_attachments_writer_policy();


-- =============================================================================
-- 7. comments
-- =============================================================================

CREATE OR REPLACE FUNCTION inform_comments_parent_integrity()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    p_article_id bigint;
    p_parent_id  bigint;
BEGIN
    IF NEW.parent_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.parent_id = NEW.id THEN
        RAISE EXCEPTION '자기 자신을 상위 댓글로 지정할 수 없습니다'
            USING ERRCODE = 'IN005';
    END IF;

    SELECT article_id, parent_id INTO p_article_id, p_parent_id
      FROM comments WHERE id = NEW.parent_id;

    IF p_article_id IS DISTINCT FROM NEW.article_id THEN
        RAISE EXCEPTION '다른 공지의 댓글을 상위 댓글로 지정할 수 없습니다'
            USING ERRCODE = 'IN005';
    END IF;

    IF p_parent_id IS NOT NULL THEN
        RAISE EXCEPTION '답글에는 다시 답글을 달 수 없습니다'
            USING ERRCODE = 'IN004';
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_comments_10_parent_integrity
    BEFORE INSERT OR UPDATE OF parent_id, article_id ON comments
    FOR EACH ROW EXECUTE FUNCTION inform_comments_parent_integrity();


-- comment_count = deleted_at IS NULL 인 행의 수.
--
-- ★ UPDATE OF article_id 를 반드시 포함한다.
--   중복 공지 병합에서 댓글을 옮길 때 이게 없으면 양쪽 개수가 조용히 틀어진다.
CREATE OR REPLACE FUNCTION inform_sync_comment_count()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    old_counted boolean;
    new_counted boolean;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.deleted_at IS NULL THEN
            UPDATE articles SET comment_count = comment_count + 1 WHERE id = NEW.article_id;
        END IF;

    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.deleted_at IS NULL THEN
            UPDATE articles SET comment_count = comment_count - 1 WHERE id = OLD.article_id;
        END IF;

    ELSE  -- UPDATE
        old_counted := OLD.deleted_at IS NULL;
        new_counted := NEW.deleted_at IS NULL;

        IF OLD.article_id = NEW.article_id THEN
            IF old_counted AND NOT new_counted THEN
                UPDATE articles SET comment_count = comment_count - 1 WHERE id = OLD.article_id;
            ELSIF NOT old_counted AND new_counted THEN
                UPDATE articles SET comment_count = comment_count + 1 WHERE id = NEW.article_id;
            END IF;
        ELSE  -- 병합 등으로 다른 공지로 이동
            IF old_counted THEN
                UPDATE articles SET comment_count = comment_count - 1 WHERE id = OLD.article_id;
            END IF;
            IF new_counted THEN
                UPDATE articles SET comment_count = comment_count + 1 WHERE id = NEW.article_id;
            END IF;
        END IF;
    END IF;

    RETURN NULL;
END $$;

CREATE TRIGGER trg_comments_90_count
    AFTER INSERT OR DELETE OR UPDATE OF deleted_at, article_id ON comments
    FOR EACH ROW EXECUTE FUNCTION inform_sync_comment_count();


-- =============================================================================
-- 8. bookmarks / article_likes 카운터
--
-- ★ UPDATE OF article_id 포함.
--   병합을 UPDATE 로 구현해도 개수가 깨지지 않게 하는 안전망이다.
--   (권장 구현은 여전히 INSERT ... ON CONFLICT DO NOTHING + DELETE 2단계다.
--    복합 PK 때문에 단일 UPDATE 는 중복 사용자에서 PK 위반으로 전체 실패한다)
-- =============================================================================

CREATE OR REPLACE FUNCTION inform_sync_bookmark_count()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE articles SET bookmark_count = bookmark_count + 1 WHERE id = NEW.article_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE articles SET bookmark_count = bookmark_count - 1 WHERE id = OLD.article_id;
    ELSIF OLD.article_id IS DISTINCT FROM NEW.article_id THEN
        UPDATE articles SET bookmark_count = bookmark_count - 1 WHERE id = OLD.article_id;
        UPDATE articles SET bookmark_count = bookmark_count + 1 WHERE id = NEW.article_id;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_bookmarks_90_count
    AFTER INSERT OR DELETE OR UPDATE OF article_id ON bookmarks
    FOR EACH ROW EXECUTE FUNCTION inform_sync_bookmark_count();


CREATE OR REPLACE FUNCTION inform_sync_like_count()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE articles SET like_count = like_count + 1 WHERE id = NEW.article_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE articles SET like_count = like_count - 1 WHERE id = OLD.article_id;
    ELSIF OLD.article_id IS DISTINCT FROM NEW.article_id THEN
        UPDATE articles SET like_count = like_count - 1 WHERE id = OLD.article_id;
        UPDATE articles SET like_count = like_count + 1 WHERE id = NEW.article_id;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_likes_90_count
    AFTER INSERT OR DELETE OR UPDATE OF article_id ON article_likes
    FOR EACH ROW EXECUTE FUNCTION inform_sync_like_count();


-- =============================================================================
-- 9. 추천 taxonomy 무결성
--
-- ★ 성격이 다른 두 규칙을 반드시 분리한다.
--
--   (A) vendor.type = 'CLUB'  — 영구 불변식.
--       vendors.type 이 불변이므로 언제 재검증해도 통과한다.
--       -> BEFORE INSERT OR UPDATE OF vendor_id 로 안전하게 걸 수 있다.
--
--   (B) club_type.is_active   — 시점 정책.
--       "비활성은 신규 선택만 막고 기존 관계는 보존한다"가 정책이므로,
--       기존 행은 검사를 통과하지 못하는 것이 정상이다.
--       -> BEFORE INSERT 전용. UPDATE 에도 걸면 관리자 태그 편집 화면의
--          흔한 구현(전체 삭제 후 전체 재삽입)에서 비활성 태그 재삽입이 막혀
--          "비활성 태그를 가진 동아리는 영원히 편집 불가"가 된다.
--
--   앱 구현 계약: 관계 저장은 replace-all 금지.
--   추가분만 INSERT ... ON CONFLICT DO NOTHING, 제거분만 DELETE 하는 delta 저장.
-- =============================================================================

CREATE OR REPLACE FUNCTION inform_vendor_club_type_vendor_check()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_type varchar(20);
BEGIN
    SELECT type INTO v_type FROM vendors WHERE id = NEW.vendor_id;
    IF v_type <> 'CLUB' THEN
        RAISE EXCEPTION '동아리 유형은 CLUB 제공처에만 붙일 수 있습니다 (vendor_id=%, type=%)',
                        NEW.vendor_id, v_type
            USING ERRCODE = 'IN009';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_vct_10_vendor_check
    BEFORE INSERT OR UPDATE OF vendor_id ON vendor_club_types
    FOR EACH ROW EXECUTE FUNCTION inform_vendor_club_type_vendor_check();


CREATE OR REPLACE FUNCTION inform_club_type_active_check()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_active boolean;
BEGIN
    SELECT is_active INTO v_active FROM club_types WHERE id = NEW.club_type_id;
    IF NOT v_active THEN
        RAISE EXCEPTION '비활성 동아리 유형은 새로 선택할 수 없습니다 (club_type_id=%)',
                        NEW.club_type_id
            USING ERRCODE = 'IN008';
    END IF;
    RETURN NEW;
END $$;

-- INSERT 전용인 점이 핵심이다.
CREATE TRIGGER trg_vct_20_active_check
    BEFORE INSERT ON vendor_club_types
    FOR EACH ROW EXECUTE FUNCTION inform_club_type_active_check();

CREATE TRIGGER trg_ucti_20_active_check
    BEFORE INSERT ON user_club_type_interests
    FOR EACH ROW EXECUTE FUNCTION inform_club_type_active_check();
