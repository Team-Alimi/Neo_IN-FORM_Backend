-- =============================================================================
-- V10. users 낙관적 잠금 + 비활성 분류 신규 선택 차단
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. users.version — 권한 변경이 조용히 되돌아가는 것을 막는다
-- -----------------------------------------------------------------------------
--
-- users 를 만지는 트랜잭션이 두 개 겹치면 나중에 커밋한 쪽이 로드 시점의 스냅샷으로
-- 행 전체를 다시 쓴다. articles(V2) 와 comments(V8) 가 이미 겪은 것과 같은 Lost Update 인데
-- users 만 version 이 없었다.
--
--   T1  DELETE /users/me         → UserService.withdraw 가 User 를 로드 (role='USER' 스냅샷)
--                                  user_preferences 정리 + bookmarks/article_likes/notifications
--                                  DELETE 3건이 도는 동안 트랜잭션이 열려 있다
--   T2  PATCH /admin/users/7/role → role='ADMIN' 으로 UPDATE. 커밋.
--                                  트리거가 user_role_logs 에 (USER→ADMIN, changed_by=관리자) 기록
--                                  관리자는 role='ADMIN' 이 담긴 200 을 받는다
--   T1  커밋                      → UPDATE users SET email=?, name=?, role='USER', status='WITHDRAWN', ...
--                                  role 이 낡은 값으로 되돌아간다
--
-- 갱신 행 수가 1이라 예외가 나지 않는다. 그런데 감사 트리거는 이 되돌림에도 반응하므로
-- user_role_logs 에 (ADMIN→USER, changed_by = 그 사용자 본인) 이 한 줄 더 남는다.
--
-- ★ ADM-16 의 존재 이유가 "누가 언제 누구에게 권한을 줬는지" 를 남기는 것인데,
--   그 기록이 사고를 남기는 대신 거짓말을 하게 된다.
--   v1 에서 DB 를 직접 고치던 시절보다 나쁘다 — 그때는 최소한 기록이 없다는 걸 알았다.
--
-- 반대 방향도 같은 결함이다. changeRole 이 나중에 커밋하면 status='ACTIVE', withdrawn_at=NULL 을
-- 되돌려 써서 방금 탈퇴한 계정이 되살아난다. ck_users_withdrawn 은 두 값이 함께 되돌아가므로
-- (ACTIVE, NULL) 조합을 정상으로 보고 통과시킨다.
--
-- version 이 있으면 나중 UPDATE 의 WHERE 에 version 조건이 붙어 0행이 되고,
-- GlobalExceptionHandler 가 이미 CONCURRENT_MODIFICATION(409) 으로 옮긴다.
-- 엔티티에는 @DynamicUpdate 도 함께 붙여, 바뀐 컬럼만 UPDATE 에 실리게 한다.
--
-- articles.version / comments.version 과 같은 형태로 맞춘다.

ALTER TABLE users
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_version CHECK (version >= 0);

COMMENT ON COLUMN users.version IS
    'JPA @Version. 권한 변경과 탈퇴·설정 변경이 서로를 덮어쓰는 것을 막는다';


-- -----------------------------------------------------------------------------
-- 2. 비활성 분류는 새로 선택할 수 없다
-- -----------------------------------------------------------------------------
--
-- categories.is_active 는 스키마 주석부터 "false = 신규 선택/분류에서 숨김" 이라고
-- 선언해 두었는데, 정작 그것을 강제하는 장치가 아무 데도 없었다.
-- club_types 는 trg_ucti_20_active_check / trg_vct_20_active_check 가 IN008 로 막는데
-- categories 만 대응이 빠져 있었다.
--
-- ★ 그래서 관리자가 분류를 비활성화하면 200 을 받지만 실제로는 아무 일도 일어나지 않았다.
--   특히 CAT-03 삭제가 막힌 관리자에게 앱이 "비활성화를 쓰세요" 라고 유일한 대안을 안내하기 때문에,
--   효과 없는 조작으로 유도한 뒤 분류가 접혔다고 믿게 만든다.
--
-- club_types 와 똑같이 INSERT 전용이다. 이미 선택해 둔 사용자의 기존 관계는 건드리지 않는다.
-- UserPreferenceRepository.replace 가 delta 만 반영하도록 만들어져 있어
-- (전체 삭제 후 재삽입이 아니라) 기존 선택은 이 트리거를 다시 거치지 않는다.
--
-- ★ article_categories 에는 일부러 걸지 않는다.
--   그쪽은 크롤러가 앱을 거치지 않고 직접 쓰는 경로다. 여기서 막으면 AI 분류가 비활성 코드를
--   내보내는 순간 공지 INSERT 자체가 실패해 수집이 통째로 멈춘다.
--   분류를 접을 때 크롤러 AI 분류 목록에서도 빼야 한다는 것은 제공처(D7 규약)와 같은 2단계이며,
--   앱은 비활성화 응답의 warning 으로 그것을 안내한다.

CREATE OR REPLACE FUNCTION inform_category_active_check()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_active boolean;
BEGIN
    SELECT is_active INTO v_active FROM categories WHERE id = NEW.category_id;
    IF NOT v_active THEN
        RAISE EXCEPTION '비활성 분류는 새로 선택할 수 없습니다 (category_id=%)', NEW.category_id
            USING ERRCODE = 'IN010';
    END IF;
    RETURN NEW;
END $$;

-- INSERT 전용인 점이 핵심이다. 기존 선택은 그대로 남는다.
CREATE TRIGGER trg_uic_20_active_check
    BEFORE INSERT ON user_interest_categories
    FOR EACH ROW EXECUTE FUNCTION inform_category_active_check();
