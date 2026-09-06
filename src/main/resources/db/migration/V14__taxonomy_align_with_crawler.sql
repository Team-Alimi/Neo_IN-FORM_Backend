-- 크롤러 팀 확정 taxonomy 에 맞춤 (2026-09-06 합의)
--
-- V13 으로 확정했던 code 중 4개가 크롤러 분류기 출력과 달라 그쪽 표기로 맞춘다.
-- 개념·표시명은 동일하므로 프론트 영향은 없다 (API 가 code 를 내보내지 않는다).
--
--   SEMINAR     -> LECTURE          (특강·세미나)
--   LANGUAGE    -> FOREIGN          (어학)
--   CERTIFICATE -> CERTIFICATION    (자격증)
--   EXTERNAL    -> ACTIVITY         (대외활동)
--
-- ★ code 는 UPDATE 로 바꿀 수 없다.
--   trg_categories_10_immutable(IN001) 과 JPA @Column(updatable=false) 가 막는다.
--   그래서 "새로 만들고 -> 관계를 옮기고 -> 옛 행을 지운다" 가 유일한 경로다.
--   name 도 UNIQUE 라, 새 행을 넣기 전에 옛 이름을 먼저 비켜 줘야 한다.


-- =============================================================================
-- 1. 온보딩 노출 플래그
-- =============================================================================
-- 지금까지 is_active 하나가 서로 다른 두 질문에 동시에 답하고 있었다.
--   (가) 크롤러가 이 분류를 붙일 수 있는가
--   (나) 사용자가 관심분야로 고를 수 있는가
--
-- ETC(기타) 때문에 둘을 나눠야 한다. 크롤러는 "어디에도 안 맞음" 을 ETC 로 표시해야
-- 하는데(분류 0개와 구분되어야 한다 — 0개는 관리자 "확인 필요" 로 잡힌다),
-- 온보딩 화면에서 "기타에 관심 있으세요?" 를 묻는 건 말이 안 된다.

ALTER TABLE categories
    ADD COLUMN is_selectable boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN categories.is_active     IS 'false = 분류·선택 양쪽에서 숨김. 기존 관계는 보존';
COMMENT ON COLUMN categories.is_selectable IS 'false = 분류에는 쓰지만 사용자가 관심분야로 고를 수는 없음 (예: 기타)';


-- =============================================================================
-- 2. code 4개 이관
-- =============================================================================
DO $$
DECLARE
    pairs text[][] := ARRAY[
        ['SEMINAR',     'LECTURE'],
        ['LANGUAGE',    'FOREIGN'],
        ['CERTIFICATE', 'CERTIFICATION'],
        ['EXTERNAL',    'ACTIVITY']
    ];
    i        int;
    old_code text;
    new_code text;
    old_id   bigint;
    new_id   bigint;
    v_name   text;
    v_sort   int;
    moved_ac int;
    moved_ui int;
BEGIN
    FOR i IN 1 .. array_length(pairs, 1) LOOP
        old_code := pairs[i][1];
        new_code := pairs[i][2];

        SELECT id, name, sort_order INTO old_id, v_name, v_sort
          FROM categories WHERE code = old_code;

        -- 없으면 조용히 넘어가지 않는다. 이미 이관됐거나 V13 이 안 돌았다는 뜻이고,
        -- 어느 쪽이든 사람이 확인해야 한다.
        IF old_id IS NULL THEN
            RAISE EXCEPTION '이관 대상 분류 % 가 없습니다. V13 적용 여부를 확인하세요.', old_code;
        END IF;

        -- (a) 이름 UNIQUE 를 비켜 준다. 이 상태는 같은 트랜잭션 안에서만 존재한다.
        UPDATE categories SET name = v_name || ' (이관중)' WHERE id = old_id;

        -- (b) 새 code 를 최종 이름·순서로 만든다
        INSERT INTO categories (code, name, sort_order)
        VALUES (new_code, v_name, v_sort)
        RETURNING id INTO new_id;

        -- (c) 공지-분류 관계 이관.
        --     ON CONFLICT 는 같은 공지가 이미 새 분류를 갖고 있는 경우를 위한 것이다.
        INSERT INTO article_categories (article_id, category_id)
             SELECT article_id, new_id FROM article_categories WHERE category_id = old_id
        ON CONFLICT DO NOTHING;
        GET DIAGNOSTICS moved_ac = ROW_COUNT;
        DELETE FROM article_categories WHERE category_id = old_id;

        -- (d) 사용자 관심분야 이관. created_at 을 보존해 "언제부터 관심이었나" 를 유지한다.
        INSERT INTO user_interest_categories (user_id, category_id, created_at)
             SELECT user_id, new_id, created_at FROM user_interest_categories WHERE category_id = old_id
        ON CONFLICT DO NOTHING;
        GET DIAGNOSTICS moved_ui = ROW_COUNT;
        DELETE FROM user_interest_categories WHERE category_id = old_id;

        -- (e) 옛 행 제거. 관계가 남아 있으면 FK(RESTRICT)가 여기서 막아 준다 —
        --     조용히 지나가는 것보다 낫다.
        DELETE FROM categories WHERE id = old_id;

        RAISE NOTICE '% -> % (공지 %건, 관심 %건 이관)', old_code, new_code, moved_ac, moved_ui;
    END LOOP;
END $$;


-- =============================================================================
-- 3. ETC 되살리기 — 분류용으로만
-- =============================================================================
-- 크롤러 분류표 12번이 ETC(기타·미분류)다. V13 에서 비활성화했는데,
-- 그대로 두면 크롤러가 ETC 로 분류한 공지를 아무도 관심분야로 고를 수 없어
-- 개인화 피드에서 영원히 사라진다. 에러도 경고도 없다.
UPDATE categories
   SET is_active     = true,
       is_selectable = false,
       name          = '기타',
       sort_order    = 999
 WHERE code = 'ETC';

-- RECRUIT(모집·선발) · GLOBAL(국제·교환) 은 크롤러 분류표에 없으므로 비활성 유지.


-- =============================================================================
-- 4. 관심분야 차단 트리거에 is_selectable 반영
-- =============================================================================
-- ETC 가 is_active=true 가 됐으므로, V10 의 활성 검사만으로는 사용자가
-- id 를 직접 실어 보내면 통과한다. 목록에서 안 보이는 것과 저장이 막히는 것은 별개다.
CREATE OR REPLACE FUNCTION inform_category_active_check()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_active     boolean;
    v_selectable boolean;
BEGIN
    SELECT is_active, is_selectable INTO v_active, v_selectable
      FROM categories WHERE id = NEW.category_id;

    IF NOT v_active THEN
        RAISE EXCEPTION '비활성 분류는 새로 선택할 수 없습니다 (category_id=%)', NEW.category_id
            USING ERRCODE = 'IN010';
    END IF;
    IF NOT v_selectable THEN
        RAISE EXCEPTION '관심분야로 고를 수 없는 분류입니다 (category_id=%)', NEW.category_id
            USING ERRCODE = 'IN010';
    END IF;
    RETURN NEW;
END $$;


-- =============================================================================
-- 5. 크롤러 중복 의심 보고 권한
-- =============================================================================
-- 크롤러가 "이거 중복 같다" 를 표시할 방법이 없었다.
-- handoff 는 status 축(DUPLICATE_SUSPECTED)으로 풀자고 하지만, 그 값은 v11 정책이
-- 이미 제거했고(similarity_score 기반 필터로 대체), 되살리면 CHECK 3개 + enum +
-- 전이 규칙 + 감사 로그 허용집합 + 관리자 대시보드까지 연쇄로 번진다.
-- 기존 similarity 축에 쓰기만 열면 같은 요구가 충족된다.
--
-- ★ version UPDATE 권한 회수는 여기 넣지 않았다.
--   크롤러가 아직 SET 목록에 version 을 싣고 있으면 회수 즉시 모든 수집이
--   permission denied 로 멈춘다. 크롤러 팀의 "뺐다" 확인 후 별도 마이그레이션으로.
GRANT UPDATE (similarity_score, similar_article_id) ON articles TO inform_crawler;


-- =============================================================================
-- 6. 확인
-- =============================================================================
DO $$
DECLARE
    n_sel int;
    n_bad int;
BEGIN
    SELECT count(*) INTO n_sel FROM categories WHERE is_active AND is_selectable;
    IF n_sel <> 11 THEN
        RAISE EXCEPTION '온보딩 노출 분류가 11개가 아닙니다 (현재 %)', n_sel;
    END IF;

    SELECT count(*) INTO n_bad FROM categories
     WHERE code IN ('SEMINAR', 'LANGUAGE', 'CERTIFICATE', 'EXTERNAL');
    IF n_bad > 0 THEN
        RAISE EXCEPTION '이관되지 않은 옛 code 가 %개 남아 있습니다', n_bad;
    END IF;

    SELECT count(*) INTO n_bad FROM (
        SELECT unnest(ARRAY['ACADEMIC','ACTIVITY','CAREER','CERTIFICATION','CONTEST',
                            'EVENT','FOREIGN','LECTURE','RESEARCH','SCHOLARSHIP',
                            'VOLUNTEER','ETC']) AS c
        EXCEPT
        SELECT code FROM categories WHERE is_active
    ) miss;
    IF n_bad > 0 THEN
        RAISE EXCEPTION '크롤러 분류표 code 중 %개가 활성 상태로 없습니다', n_bad;
    END IF;
END $$;
