-- =============================================================================
-- V6. published_at 의 소유권을 DB 로 옮긴다
-- =============================================================================
--
-- 그 전까지는 애플리케이션이 OffsetDateTime.now() 로 찍었다. 이 테이블에서
-- 유일하게 DB 가 소유하지 않는 시각이었고, 두 가지가 걸렸다.
--
--   1) 인스턴스가 여러 대면 시계가 조금씩 어긋나 발행 순서가 뒤집힌다.
--      피드가 published_at DESC 정렬이므로 나중에 발행한 글이 아래로 내려간다.
--
--   2) ★ 앱을 거치지 않는 발행 경로가 있다.
--      크롤러는 정상 공지를 검수 없이 곧바로 노출시키려고
--      status='PUBLISHED' 로 직접 INSERT 한다. 그런데 크롤러에게는
--      published_at UPDATE 권한이 없고(V5 의 컬럼 단위 GRANT),
--      ck_articles_published 는 PUBLISHED 인데 published_at 이 NULL 이면 거부한다.
--      즉 크롤러가 값을 스스로 채우거나, DB 가 채워 주거나 둘 중 하나여야 한다.
--      트리거로 채우면 크롤러는 status 만 지정하면 되고, 시각의 일관성도 DB 가 보장한다.
--
-- 결과적으로 앱·크롤러·관리자 어느 경로로 발행하든 같은 시계를 쓰게 된다.


-- 이미 있는 값은 존중한다. 두 경우가 여기 해당한다.
--   - 원본 게시일을 알고 있어 크롤러가 직접 넣는 경우
--   - 배포를 취소했다가 다시 올리는 경우 (최초 발행 시각을 유지해야 한다)
-- "PUBLISHED 로 바뀌었는데 비어 있을 때만" 채우므로 재발행이 시각을 갱신하지 않는다.
CREATE OR REPLACE FUNCTION inform_articles_published_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'PUBLISHED' AND NEW.published_at IS NULL THEN
        NEW.published_at := now();
    END IF;
    RETURN NEW;
END $$;

COMMENT ON FUNCTION inform_articles_published_at() IS
    'PUBLISHED 진입 시 발행 시각을 채운다. 이미 값이 있으면 건드리지 않는다';


-- ★ 이름의 25 가 실행 순서를 정한다. 20 과 30 사이여야 한다.
--
--   20 (crawler_policy) 뒤 — 20 이 크롤러의 본문 수정을 PENDING_REVIEW 로 강등시킬 수 있다.
--        25 가 먼저 돌면 강등될 예정인 행에 발행 시각을 찍는다.
--   40 (updated_at) 앞 — 40 의 화이트리스트에 published_at 이 들어 있다.
--        25 가 나중에 돌면 발행 시각 변경이 수정 시각에 반영되지 않는다.
--
-- INSERT 에도 거는 이유는 위 2) 다. articles 의 첫 BEFORE INSERT 트리거다.
CREATE TRIGGER trg_articles_25_published_at
    BEFORE INSERT OR UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION inform_articles_published_at();


-- 기존 행 보정은 하지 않는다.
-- ck_articles_published 가 처음부터 PUBLISHED + published_at IS NULL 을 거부해 왔으므로
-- 보정 대상이 존재할 수 없다.
