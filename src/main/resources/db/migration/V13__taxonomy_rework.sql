-- 온보딩 화면 확정에 따른 taxonomy 정리 (2026-09-01 결정)
--
--   관심 공지 분야  8개 → 11개
--   동아리 유형     9개 →  8개
--
-- ★ code 는 절대 바꾸지 않습니다.
--   categories.code 주석: "크롤러 AI 분류 계약 키. 생성 후 변경 금지".
--   크롤러가 이 문자열로 분류 결과를 보내므로, 바꾸면 기존 분류가 전부 깨집니다.
--   따라서 표시명(name)만 바꾸고, 빠지는 항목은 지우지 않고 is_active=false 로 숨깁니다.
--
-- ★ 삭제가 아니라 비활성인 이유는 두 가지입니다.
--   1) FK 가 ON DELETE RESTRICT 라 참조가 하나라도 있으면 DELETE 가 실패합니다.
--   2) is_active 주석: "false = 신규 선택/분류에서 숨김. 기존 관계는 보존".
--      이미 그 분류로 붙은 공지와 사용자 관심은 그대로 남습니다.


-- =============================================================================
-- 1. 관심 공지 분야 — 11개
-- =============================================================================

-- 표시명만 변경 (code 유지)
UPDATE categories SET name = '장학금',      sort_order = 20 WHERE code = 'SCHOLARSHIP';
UPDATE categories SET name = '학사',        sort_order = 10 WHERE code = 'ACADEMIC';
UPDATE categories SET name = '공모전·대회', sort_order = 30 WHERE code = 'CONTEST';
UPDATE categories SET name = '취업·인턴십', sort_order = 50 WHERE code = 'CAREER';
UPDATE categories SET name = '행사·축제',   sort_order = 60 WHERE code = 'EVENT';

-- 신규 6개
INSERT INTO categories (code, name, sort_order) VALUES
    ('SEMINAR',     '특강·세미나', 40),
    ('VOLUNTEER',   '봉사활동',    70),
    ('LANGUAGE',    '어학',        80),
    ('CERTIFICATE', '자격증',      90),
    ('RESEARCH',    '학술·연구',  100),
    ('EXTERNAL',    '대외활동',   110)
ON CONFLICT (code) DO NOTHING;

-- 온보딩 목록에서 빠지는 3개 — 숨기되 보존
--
-- 비활성 차단 트리거(trg_uic_20_active_check)는 user_interest_categories 에만
-- 걸려 있고 article_categories 에는 없습니다. 즉 크롤러는 이 셋으로 계속
-- 분류할 수 있고 INSERT 가 실패하지 않습니다. 막히는 건 "사용자가 새로
-- 관심으로 고르는 것" 뿐입니다.
--
-- ★ 그래서 오히려 조용한 문제가 됩니다.
--   이 셋으로 분류된 공지는 아무도 관심 등록을 할 수 없으므로
--   interest_only 필터에 영원히 안 잡힙니다. 에러도 경고도 없습니다.
--   V10 주석이 말하는 "분류를 접을 때 크롤러 AI 분류 목록에서도 빼야 한다"가
--   이것이며, DB 비활성화와 크롤러 설정 변경은 2단계로 나뉩니다.
--
-- ★ 크롤러 팀에 전달할 것 (양방향입니다)
--   1) RECRUIT / GLOBAL / ETC 를 분류 결과로 내보내지 말 것.
--      분류가 애매하면 기타로 넣지 말고 아무 분류도 붙이지 않으면 됩니다
--      (article_categories 는 행이 0개여도 됩니다).
--   2) 신규 6개(SEMINAR / VOLUNTEER / LANGUAGE / CERTIFICATE / RESEARCH /
--      EXTERNAL)를 분류 대상에 추가할 것. 안 하면 온보딩에서 고를 수는 있는데
--      그 분류로 오는 공지가 하나도 없어 빈 피드가 됩니다.
UPDATE categories SET is_active = false WHERE code IN ('RECRUIT', 'GLOBAL', 'ETC');


-- =============================================================================
-- 2. 동아리 유형 — 8개 (피그마 기준)
-- =============================================================================

UPDATE club_types SET name = '학술/IT',    sort_order = 10 WHERE code = 'ACADEMIC';
UPDATE club_types SET name = '체육/스포츠', sort_order = 20 WHERE code = 'SPORTS';
UPDATE club_types SET name = '음악/공연',  sort_order = 30 WHERE code = 'PERFORM';
UPDATE club_types SET name = '봉사',       sort_order = 40 WHERE code = 'VOLUNTEER';
UPDATE club_types SET name = '문화·예술',  sort_order = 50 WHERE code = 'CULTURE';
UPDATE club_types SET name = '창업',       sort_order = 60 WHERE code = 'STARTUP';
UPDATE club_types SET name = '종교',       sort_order = 80 WHERE code = 'RELIGION';

INSERT INTO club_types (code, name, sort_order) VALUES
    ('DANCE', '댄스', 70)
ON CONFLICT (code) DO NOTHING;

-- 피그마에 없는 2개
UPDATE club_types SET is_active = false WHERE code IN ('LANGUAGE', 'ETC');


-- =============================================================================
-- 3. 확인 — 개수가 안 맞으면 여기서 실패시킵니다
-- =============================================================================
-- 조용히 어긋난 채 배포되면 온보딩 화면이 이상해지는데,
-- 그때는 원인이 마이그레이션이라는 걸 아무도 떠올리지 못합니다.
DO $$
DECLARE c int; t int;
BEGIN
    SELECT count(*) INTO c FROM categories WHERE is_active;
    SELECT count(*) INTO t FROM club_types WHERE is_active;
    IF c <> 11 THEN
        RAISE EXCEPTION '활성 카테고리가 11개가 아닙니다 (현재 %)', c;
    END IF;
    IF t <> 8 THEN
        RAISE EXCEPTION '활성 동아리 유형이 8개가 아닙니다 (현재 %)', t;
    END IF;
END $$;
