-- =============================================================================
-- V7. 크롤러가 공지를 실제로 바꾸면 DB 가 version 을 올린다
-- =============================================================================
--
-- 그 전까지는 크롤러가 자기 UPDATE 문에 version = version + 1 을 직접 써넣기로
-- "약속" 되어 있었다(V5 GRANT 주석). DB 는 그걸 확인하지 않았다.
--
-- 약속만으로는 부족한 이유:
--
--   version 은 앱의 낙관적 잠금 기준이다. 관리자가 글을 읽은 뒤 저장할 때
--   UPDATE ... WHERE id = ? AND version = ? 로 "내가 본 이후 아무도 안 건드렸다" 를 확인한다.
--   크롤러가 version 을 올리지 않으면 이 확인이 통과해 버린다.
--
--     10:00:00.010  앱    : 42번 읽음 (status=READY_TO_PUBLISH, version=7)
--     10:00:00.050  크롤러: 본문 변경 감지 -> 트리거가 PENDING_REVIEW 로 강등
--                          version 은 7 그대로
--     10:00:00.100  앱    : "READY_TO_PUBLISH -> PUBLISHED 허용" 판정
--                          UPDATE ... WHERE id=42 AND version=7  -> 통과
--                          => 재검수가 필요한 글이 검수 없이 발행된다
--
--   강등뿐 아니라 본문 수정 전반이 같다. 크롤러가 본문을 갱신하는 동안
--   관리자가 제목을 고쳐 저장하면 크롤러의 갱신이 그대로 사라진다.
--
-- 크롤러가 GRANT 상 바꿀 수 있는 컬럼은 title/content/starts_on/ends_on 뿐이므로
-- (version 제외) 이 넷을 비교하면 크롤러발 변경을 빠짐없이 잡는다.
--
-- ★ 실제로 바뀐 경우에만 올린다.
--   크롤러는 내용이 그대로여도 수집 주기마다 UPDATE 를 날린다(V3 4-2 주석 참조).
--   무조건 올리면 주기마다 전체 공지의 version 이 튀고,
--   그 순간에 저장하던 관리자가 아무 이유 없이 409 를 받는다.
--   변경이 없으면 앱의 스냅샷도 여전히 유효하므로 올릴 이유가 없다.
--
-- 크롤러 쪽 영향: 없다. version = version + 1 을 계속 보내도 결과가 같고
--                (OLD.version + 1 로 대입하므로 두 번 오르지 않는다),
--                SET 목록에서 빼도 DB 가 대신 올린다.


CREATE OR REPLACE FUNCTION inform_articles_crawler_policy()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- ★ 판별은 session_user 다. SET ROLE 을 써도 원래 로그인 롤이 유지된다.
    IF session_user <> 'inform_crawler' THEN
        RETURN NEW;
    END IF;

    -- 실제 변경이 없으면 아무것도 하지 않는다. no-op UPDATE 에 반응하면
    -- 크롤링 주기마다 전체 공지가 검수 큐로 쏟아지고 version 도 무의미하게 튄다.
    IF (OLD.title, OLD.content, OLD.starts_on, OLD.ends_on)
       IS NOT DISTINCT FROM
       (NEW.title, NEW.content, NEW.starts_on, NEW.ends_on)
    THEN
        RETURN NEW;
    END IF;

    -- ① 앱의 낙관적 잠금이 이 변경을 감지할 수 있게 한다.
    --    크롤러가 무엇을 보냈든 OLD 기준으로 대입하므로 중복 증가가 없다.
    NEW.version := OLD.version + 1;

    -- ② 재검수 강등 (D9). SCHOOL 공지에만 해당한다.
    --    이미 휴지통에 있는 건 관리자의 명시적 결정이므로 되살리지 않는다.
    --    published_at 은 유지한다 — 신규 수집분(NULL)과 재검수 건을 구분하는 표식이다.
    IF NEW.source_type = 'SCHOOL' AND OLD.status <> 'TRASHED' THEN
        NEW.status := 'PENDING_REVIEW';
    END IF;

    RETURN NEW;
END $$;

COMMENT ON FUNCTION inform_articles_crawler_policy() IS
    '크롤러발 실제 변경에 대해 version 을 올리고, SCHOOL 공지를 재검수 대기로 되돌린다';

-- 트리거 자체(trg_articles_20_crawler_policy)는 그대로 둔다.
-- CREATE OR REPLACE FUNCTION 이므로 재생성이 필요 없다.
