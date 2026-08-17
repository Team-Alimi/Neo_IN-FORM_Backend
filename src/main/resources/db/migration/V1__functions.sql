-- =============================================================================
-- V1. 확장 확인 + 공용 함수
--
-- 테이블(V2)이 여기 정의된 함수를 GENERATED 컬럼에서 참조하므로 반드시 먼저 실행된다.
--
-- ★ CREATE EXTENSION은 여기 없다.
--   pg_bigm은 trusted extension이 아니라 슈퍼유저만 설치할 수 있고,
--   Flyway는 앱 계정(inform)으로 실행되므로 권한이 없다.
--   대신 docker/db/initdb/01-init.sh 가 컨테이너 최초 기동 시 설치한다.
--   여기서는 "설치되어 있는가"만 확인해 원인이 분명한 에러를 낸다.
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_bigm') THEN
        RAISE EXCEPTION
            'pg_bigm 확장이 설치되어 있지 않습니다. docker/db/Dockerfile 로 빌드한 이미지를 쓰고 있는지, docker/db/initdb/01-init.sh 가 실행되었는지 확인하세요. (docker compose down -v 후 재기동하면 initdb가 다시 돕니다)';
    END IF;
END
$$;


-- -----------------------------------------------------------------------------
-- 검색 정규화
--
-- articles.search_text 가 GENERATED STORED 로 이 함수를 호출한다.
--
-- ★ 반드시 IMMUTABLE 이어야 GENERATED 컬럼에 쓸 수 있다.
--
-- ★★ 경고 — 이 함수의 본문을 나중에 바꿔도 기존 행의 search_text 는
--    다시 계산되지 않는다. 정의를 바꾸려면 컬럼을 DROP 후 재생성해야 하고
--    (PG16에는 ALTER COLUMN ... SET EXPRESSION 이 없다) GIN 인덱스도 함께
--    재구축된다. 그래서 처음부터 세 가지를 모두 처리한다.
--      1) HTML 태그 제거   — content 는 sanitize 되어 있을 뿐 태그가 남아 있다.
--                            안 지우면 'class', 'http', 'inha' 같은 문자열이
--                            거의 모든 행에 들어가 bigm 인덱스가 무력해진다.
--      2) HTML 엔티티 제거 — &nbsp; &amp; &#39; 등
--      3) 연속 공백 정리
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inform_normalize_search(p_title text, p_content text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT btrim(
        regexp_replace(
            regexp_replace(
                regexp_replace(
                    lower(coalesce(p_title, '') || ' ' || coalesce(p_content, '')),
                    '<[^>]*>', ' ', 'g'),          -- 1) 태그
                '&[a-z]+;|&#[0-9]+;', ' ', 'g'),   -- 2) 엔티티
            '\s+', ' ', 'g')                       -- 3) 공백
    );
$$;

COMMENT ON FUNCTION inform_normalize_search(text, text) IS
    'articles.search_text 생성식. 태그/엔티티 제거 후 소문자화. 변경 시 컬럼 재생성 필요.';


-- -----------------------------------------------------------------------------
-- 감사 메타데이터 헬퍼
--
-- 관리자 API는 트랜잭션 첫 문장에서 다음을 호출해야 한다.
--     SELECT set_config('app.changed_by_user_id', :userId::text, true);
--                                                              ^^^^ true = SET LOCAL
--
-- ★ SET LOCAL 문은 바인드 파라미터를 받지 못한다. 반드시 set_config()를 쓸 것.
-- ★ 3번째 인자를 false 로 주면 커넥션 풀 반납 후에도 값이 남아
--   다음 요청이 엉뚱한 사람 이름으로 감사 로그를 남긴다.
--
-- 값이 없으면 NULL 을 돌려주고, 그건 "크롤러/스케줄러가 한 변경"을 뜻한다.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inform_current_actor()
RETURNS bigint
LANGUAGE sql
STABLE
AS $$
    SELECT nullif(current_setting('app.changed_by_user_id', true), '')::bigint;
$$;

-- 상태 변경 사유(article_status_logs.memo). 트리거가 자동 기록하므로
-- 사유를 남기려면 마찬가지로 트랜잭션-로컬 GUC 로 전달한다.
--     SELECT set_config('app.status_change_memo', :memo, true);
CREATE OR REPLACE FUNCTION inform_current_memo()
RETURNS varchar
LANGUAGE sql
STABLE
AS $$
    SELECT left(nullif(current_setting('app.status_change_memo', true), ''), 500);
$$;


-- -----------------------------------------------------------------------------
-- 공용 updated_at
-- articles 는 규칙이 달라 별도 함수를 쓴다(V3 참조).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inform_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;
