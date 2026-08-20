-- =============================================================================
-- V9. comments.updated_at 을 화이트리스트 방식으로
-- =============================================================================
--
-- 지금은 공용 함수 inform_set_updated_at() 이 걸려 있어 <b>어떤 UPDATE 에도</b>
-- updated_at 을 현재 시각으로 밀어 올린다. 그런데 앱은 이 컬럼을 화면 표시에 쓴다 —
-- created_at 과 다르면 "수정됨" 을 붙인다(CommentRow.isEdited).
--
-- 그래서 내용과 무관한 UPDATE 가 사용자에게 거짓말을 한다.
--
--   1) 중복 공지 병합(ADM-13) — 댓글을 옮기려고 article_id 를 바꾸면
--      옮겨진 댓글 <b>전부</b>가 "수정됨" 으로 표시된다.
--      관리자가 공지 두 개를 합쳤을 뿐인데 사용자가 쓴 댓글이 고쳐진 것처럼 보인다.
--   2) 댓글 삭제 — deleted_at 만 세워도 수정 시각이 바뀐다.
--
-- articles 는 이미 화이트리스트 방식이다(V2/V3 의 inform_articles_updated_at).
-- 같은 이유로 comments 도 "본문이 실제로 바뀐 경우에만" 갱신한다.
--
-- ★ 제외 목록이 아니라 포함 목록으로 판정한다.
--   제외 방식이면 컬럼이 늘어날 때마다 목록을 넓혀야 하고, 하나 빠뜨리면
--   같은 종류의 거짓말이 조용히 다시 생긴다.

CREATE OR REPLACE FUNCTION inform_comments_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.content IS DISTINCT FROM OLD.content THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END $$;

COMMENT ON FUNCTION inform_comments_updated_at() IS
    '본문이 실제로 바뀐 경우에만 수정 시각을 갱신한다. 이동·삭제는 수정이 아니다';

DROP TRIGGER IF EXISTS trg_comments_updated_at ON comments;

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION inform_comments_updated_at();
