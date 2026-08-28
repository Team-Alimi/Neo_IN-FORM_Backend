-- ============================================================================
-- V12. 추천(좋아요) 기능 제거
--
-- 북마크 하나가 "저장" 과 "관심의 척도" 두 역할을 겸하기로 했다(2026-08-25 회의).
-- 북마크와 추천이 결국 같은 것을 표현한다는 판단이다.
--
-- 인기 공지 정렬은 원래부터 bookmark_count 기준이었으므로 이 삭제로 바뀌는 것이 없다.
-- like_count 를 실제로 읽는 곳은 없었다.
--
-- ★ 되돌릴 수 없다. article_likes 의 행이 사라진다.
--   되살리려면 테이블·컬럼·트리거·인덱스를 다시 만들고 데이터는 포기해야 한다.
-- ============================================================================

-- 1) 트리거 먼저. 테이블을 지우면 같이 사라지지만, 순서를 명시해 의도를 남긴다.
DROP TRIGGER IF EXISTS trg_likes_90_count ON article_likes;
DROP FUNCTION IF EXISTS inform_sync_like_count();

-- 2) 인덱스
--    idx_likes_article 은 테이블과 함께 사라지지만 명시한다.
DROP INDEX IF EXISTS idx_articles_liked;
DROP INDEX IF EXISTS idx_likes_article;

-- 3) 테이블
DROP TABLE IF EXISTS article_likes;

-- 4) 카운터 컬럼
--    articles 를 읽는 코드가 이 컬럼을 더 이상 매핑하지 않는다(Article 엔티티에서 제거).
ALTER TABLE articles DROP COLUMN IF EXISTS like_count;

COMMENT ON COLUMN articles.bookmark_count IS
    '북마크 수. 저장이자 관심의 척도다 — 인기 공지 정렬 기준이며, 추천 제거(V12) 이후 유일한 반응 지표.';
