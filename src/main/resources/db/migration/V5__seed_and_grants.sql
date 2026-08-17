-- =============================================================================
-- V5. 초기 마스터 데이터 + 크롤러 권한
--
-- ★ ON CONFLICT DO NOTHING 을 쓴다 (DO UPDATE 아님).
--   운영에서 관리자가 표시명(name)이나 정렬 순서를 고친 뒤 재배포해도
--   시드가 덮어쓰지 않는다. code 가 이미 있으면 아무것도 하지 않는다.
--
-- ★ 항목을 추가할 때는 이 파일을 고치지 말고 새 마이그레이션(V6__…)을 만든다.
--   Flyway 는 실행된 파일의 체크섬을 검증하므로 수정하면 앱이 켜지지 않는다.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 공지 카테고리
--
-- code 는 크롤러 AI 분류 출력과 매칭되는 계약 키다. 변경 금지.
-- name 은 화면 표시용이라 자유롭게 바꿔도 된다.
-- ※ 아래 목록은 초안이다. 크롤러 팀의 분류 목록과 맞춰 확정할 것.
-- -----------------------------------------------------------------------------
INSERT INTO categories (code, name, sort_order) VALUES
    ('SCHOLARSHIP',  '장학',        10),
    ('ACADEMIC',     '학사',        20),
    ('RECRUIT',      '모집·선발',   30),
    ('CONTEST',      '공모전',      40),
    ('CAREER',       '취업·인턴',   50),
    ('EVENT',        '행사',        60),
    ('GLOBAL',       '국제·교환',   70),
    ('ETC',          '기타',        99)
ON CONFLICT (code) DO NOTHING;


-- -----------------------------------------------------------------------------
-- 동아리 유형 (추천 taxonomy)
--
-- 온보딩에서 사용자가 고르고, vendor_club_types 로 동아리에 태깅된다.
-- 추천 점수 = 사용자와 동아리가 공유하는 active 유형 수.
-- ※ 아래 목록도 초안이다. 실제 동아리 분류와 맞춰 확정할 것.
-- -----------------------------------------------------------------------------
INSERT INTO club_types (code, name, sort_order) VALUES
    ('ACADEMIC',   '학술·전공',   10),
    ('VOLUNTEER',  '봉사',        20),
    ('PERFORM',    '공연·음악',   30),
    ('SPORTS',     '체육',        40),
    ('CULTURE',    '문화·예술',   50),
    ('STARTUP',    '창업',        60),
    ('LANGUAGE',   '어학',        70),
    ('RELIGION',   '종교',        80),
    ('ETC',        '기타',        99)
ON CONFLICT (code) DO NOTHING;


-- -----------------------------------------------------------------------------
-- 제공처 (학과·기관·동아리)
--
-- ★ 의도적으로 비어 있다.
--   initial 은 크롤러의 사이트별 식별자와 1:1로 맞아야 하는 계약 키이므로
--   추측해서 넣으면 안 된다. 규약은 "DB(관리자 페이지)에 먼저 등록 → 크롤러
--   시드/설정에 추가" 순서다.
--
--   등록 방법 두 가지 중 하나를 택한다.
--     (1) 관리자 페이지 VND-01 로 등록 (운영 원칙에 부합)
--     (2) 초기 대량 등록이 필요하면 V6__seed_vendors.sql 을 새로 만들어 넣기
--
--   예시 형태:
--     INSERT INTO vendors (name, initial, type, homepage_url) VALUES
--       ('컴퓨터공학과', 'CSE',  'SCHOOL', 'https://cse.inha.ac.kr'),
--       ('IT동아리 OOO', 'CLUB_OOO', 'CLUB', NULL)
--     ON CONFLICT (initial) DO NOTHING;
-- -----------------------------------------------------------------------------


-- =============================================================================
-- 크롤러 권한 (컬럼 단위 GRANT)
--
-- 롤 자체는 docker/db/initdb/01-init.sh 가 슈퍼유저로 만든다.
-- 여기서는 테이블이 생긴 뒤에만 가능한 권한 부여만 처리한다.
--
-- ★ 핵심 설계: UPDATE 대상 컬럼에 status 와 카운터를 넣지 않는다.
--   그래서 "크롤러는 공지를 배포하거나 카운터를 조작할 수 없다"가
--   앱 코드가 아니라 DB 레벨에서 강제된다.
--   검수 대기로의 강등은 trg_articles_20_crawler_policy 트리거가 대신 수행한다.
--   (BEFORE 트리거가 NEW 를 수정하는 것은 컬럼 권한 검사 대상이 아니다)
--
-- ★ summary 는 UPDATE 목록에서 뺀다.
--   무효화는 trg_articles_30_summary_invalidate 가 단독으로 책임지므로
--   크롤러가 요약을 직접 건드릴 이유가 없다.
--
-- ★ version 은 반드시 포함한다.
--   크롤러가 UPDATE 시 version = version + 1 을 수행해야
--   관리자 편집과의 Lost Update 를 낙관적 락이 감지할 수 있다.
-- =============================================================================

-- 문자열 business key 해석용 (숫자 id 하드코딩 금지 규약)
GRANT SELECT ON vendors, categories TO inform_crawler;

-- 공지 본체
GRANT SELECT, INSERT ON articles TO inform_crawler;
GRANT UPDATE (title, content, starts_on, ends_on, version) ON articles TO inform_crawler;

-- 출처·분류·첨부
GRANT SELECT, INSERT, UPDATE, DELETE ON article_vendors     TO inform_crawler;
GRANT SELECT, INSERT,         DELETE ON article_categories  TO inform_crawler;
GRANT SELECT, INSERT, UPDATE, DELETE ON attachments         TO inform_crawler;

-- bigserial 시퀀스 사용 권한 (INSERT 하려면 필요)
GRANT USAGE, SELECT ON SEQUENCE articles_id_seq        TO inform_crawler;
GRANT USAGE, SELECT ON SEQUENCE article_vendors_id_seq TO inform_crawler;
GRANT USAGE, SELECT ON SEQUENCE attachments_id_seq     TO inform_crawler;

-- 크롤러는 사용자 데이터·감사 로그·서비스 공지에 일절 접근하지 않는다.
--
-- ★ article_status_logs 에 GRANT 를 주지 않는 것이 의도다.
--   크롤러가 직접 쓸 수 있으면 감사 로그를 위조할 수 있다.
--   대신 inform_articles_status_audit() 을 SECURITY DEFINER 로 만들어
--   트리거를 통한 기록만 가능하게 했다(V3 참조).
--   이 함수가 SECURITY INVOKER 로 되돌아가면 크롤러의 모든 INSERT/UPDATE 가
--   permission denied 로 실패한다 — 회귀 테스트로 고정해 둘 것.
