-- 공지사항: "상단 고정" 개념을 버리고 "팝업" 으로 통일 (2026-10-05 결정)
--
-- 원래 is_pinned 는 목록 상단 고정용이었는데, 운영에서 쓰기로 한 것은 팝업 하나뿐이다.
-- 플래그를 그대로 재활용하되 이름을 맞춘다 — 옛 이름을 두면 나중에 읽는 사람이
-- "상단 고정" 으로 읽고, 목록 정렬에 쓰려다 팝업이 딸려 나오는 식으로 어긋난다.
--
-- ★ 지금 바꾸는 것이 공짜인 이유
--   이 테이블을 읽거나 쓰는 애플리케이션 코드가 아직 하나도 없다
--   (announcement 패키지에 .gitkeep 만 있다). 그래서 행도 0 건이고,
--   값 변환도 코드 수정도 필요 없다. 기능을 붙인 뒤에는 같은 변경에
--   엔티티·DTO·명세서가 전부 딸려 온다.

ALTER TABLE announcements RENAME COLUMN is_pinned TO is_popup;

COMMENT ON COLUMN announcements.is_popup IS
    '앱 진입 시 팝업으로 띄울지. 목록 노출과는 별개이며, 여러 건이 동시에 켜질 수 있다';


-- =============================================================================
-- 인덱스 재정의
-- =============================================================================
-- RENAME 자체는 인덱스를 따라오게 한다 — PostgreSQL 의 인덱스는 이름이 아니라
-- 컬럼 자체를 가리키므로 idx_ann_feed 의 정의가 자동으로 갱신된다.
--
-- ★ 그런데 정의가 더 이상 맞지 않는다.
--   기존: (is_pinned DESC, published_at DESC, id DESC) WHERE status = 'PUBLISHED'
--   선두 컬럼이 "고정 먼저" 라는 정렬 규칙이었다. 그 규칙을 없앴으므로
--   목록(ORDER BY published_at DESC, id DESC)은 이 인덱스로 정렬을 못 타고,
--   무엇보다 인덱스를 읽는 사람에게 "팝업이 목록 맨 위로 올라간다" 고 말한다.
--   없앤 동작을 스키마가 계속 주장하는 상태라 다시 만든다.
--
-- 팝업 전용 인덱스는 두지 않는다. 팝업 조회도 같은 부분 인덱스
-- (status = 'PUBLISHED')를 훑고 is_popup 으로 걸러내면 되고, 이 테이블은
-- 운영 공지라 수십 건 규모다. 건수가 늘면 그때 근거를 가지고 추가한다.
DROP INDEX idx_ann_feed;

CREATE INDEX idx_ann_feed ON announcements (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';


-- =============================================================================
-- 검증
-- =============================================================================
-- 컬럼이 실제로 바뀌었고 옛 이름이 남아 있지 않은지 확인한다.
-- RENAME 은 조용히 실패하지 않지만, 수기 복구 등으로 두 컬럼이 공존하는 상태를
-- 즉시 드러내기 위해 둔다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'announcements' AND column_name = 'is_popup'
    ) THEN
        RAISE EXCEPTION 'announcements.is_popup 이 없습니다 — RENAME 이 적용되지 않았습니다';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'announcements' AND column_name = 'is_pinned'
    ) THEN
        RAISE EXCEPTION 'announcements.is_pinned 가 남아 있습니다 — 두 컬럼이 공존합니다';
    END IF;

    -- 인덱스가 다시 만들어졌는지. DROP 만 적용되고 CREATE 가 빠지면
    -- 목록 조회가 조용히 느려지기만 해서 알아채기 어렵다.
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE tablename = 'announcements' AND indexname = 'idx_ann_feed'
    ) THEN
        RAISE EXCEPTION 'idx_ann_feed 가 없습니다 — 인덱스 재생성이 빠졌습니다';
    END IF;
END $$;
