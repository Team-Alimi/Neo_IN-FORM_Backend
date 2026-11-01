-- =============================================================================
-- V19. 원문 게시일을 published_at 과 분리한다 (2026-11-02)
-- =============================================================================
--
-- 크롤러가 학교 원문의 게시일을 넣어 주기로 했다. 그런데 그것을 published_at 에
-- 넣으면 안 된다는 것이 크롤러 팀 검토에서 드러났다.
--
-- ★ published_at 은 날짜가 아니라 "공개 승인 증거" 로도 쓰이고 있다.
--
--     ArticleReadableChecker:33
--     ArticleQueryRepository:73  (VISIBLE_OR_UNDER_REVIEW)
--
--       (a.status = 'PUBLISHED'
--        OR (a.status = 'PENDING_REVIEW' AND a.published_at IS NOT NULL))
--
--   두 번째 항은 "배포됐다가 크롤러 수정으로 재검수로 내려간 공지" 를 위한 것이다.
--   사용자가 저장해 둔 공지가 404 가 되면 안 되기 때문이다. 그 판정이 성립하는 근거가
--   "아직 한 번도 배포된 적 없는 신규 수집분에는 published_at 이 없다" 였다.
--
--   원문 게시일을 published_at 에 넣는 순간 그 전제가 깨진다. 검수 전 공지 약 2,000건이
--   상세 조회 · 북마크 · 좋아요 · 댓글 · AI 요약 생성까지 열린다.
--   (피드 목록 · 검색 · 인기 공지 · 캘린더는 status='PUBLISHED' 만 보므로 영향 없다)
--
-- 그래서 가시성 판정식은 건드리지 않고 컬럼을 나눈다.
--
--   published_at         우리가 공개한 시각. 가시성 판정 근거.   ← 의미 변경 없음
--   source_published_at  학교 원문 게시일. 표시 전용.            ← 신규
--
-- 판정식을 고치는 쪽은 택하지 않았다. 그 네 줄이 지금 미검수 2,000건을 막고 있는
-- 코드라서, 거기에 손대는 것 자체가 이 변경에서 가장 큰 위험이다.

ALTER TABLE articles ADD COLUMN source_published_at timestamptz;

COMMENT ON COLUMN articles.source_published_at IS
    '학교 원문의 게시일. 표시 전용 — 가시성/접근 판정에 절대 쓰지 않는다. V19';


-- =============================================================================
-- 크롤러 권한
-- =============================================================================
-- INSERT 는 이미 테이블 단위로 열려 있어(V5) 별도 부여가 필요 없다.
-- UPDATE 를 여는 이유는 이미 수집된 2,000여 건을 소급해 채울 수 있어야 하기 때문이다.
--
-- ★ 이 컬럼에 UPDATE 를 열어도 안전하다. 어떤 가시성 판정도 이 값을 보지 않는다.
--   published_at 에는 여전히 UPDATE 권한이 없다 — 크롤러가 공개 여부를 바꿀 수 없다는
--   V5 의 설계가 그대로 유지된다.
GRANT UPDATE (source_published_at) ON articles TO inform_crawler;


-- =============================================================================
-- 검증
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'articles' AND column_name = 'source_published_at'
    ) THEN
        RAISE EXCEPTION 'articles.source_published_at 이 없습니다';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.column_privileges
         WHERE table_name = 'articles' AND column_name = 'source_published_at'
           AND grantee = 'inform_crawler' AND privilege_type = 'UPDATE'
    ) THEN
        RAISE EXCEPTION '크롤러에 source_published_at UPDATE 권한이 없습니다';
    END IF;

    -- ★ 크롤러가 published_at 을 고칠 수 없다는 V5 의 불변식을 여기서 다시 확인한다.
    --   이 마이그레이션의 전제이고, 깨지면 분리한 의미가 사라진다.
    IF EXISTS (
        SELECT 1 FROM information_schema.column_privileges
         WHERE table_name = 'articles' AND column_name = 'published_at'
           AND grantee = 'inform_crawler' AND privilege_type = 'UPDATE'
    ) THEN
        RAISE EXCEPTION '크롤러가 published_at 을 UPDATE 할 수 있습니다 — 분리의 전제가 깨집니다';
    END IF;
END $$;
