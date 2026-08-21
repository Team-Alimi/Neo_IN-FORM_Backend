-- =============================================================================
-- V11. 캘린더 겹침 조회용 인덱스 교체
-- =============================================================================
--
-- CAL-01 은 비로그인으로 열려 있어 누구나 아무리 자주 부를 수 있는데,
-- 겹침 조건이 인덱스를 하나도 타지 못하고 있었다.
--
--   WHERE status = 'PUBLISHED'
--     AND (starts_on IS NOT NULL OR ends_on IS NOT NULL)
--     AND (starts_on IS NULL OR starts_on <= :monthEnd)
--     AND (ends_on   IS NULL OR ends_on   >= :monthStart)
--
-- OR-with-NULL 은 B-tree 로 만족시킬 수 없다. idx_articles_deadline 은
-- WHERE ends_on IS NOT NULL 부분 인덱스라 "마감일 없는 공지" 를 담고 있지 않고,
-- starts_on 에는 인덱스가 아예 없다. 남는 것은 status 뿐이라
-- 배포 공지 전체를 훑고 힙을 읽은 뒤 COALESCE 정렬까지 하게 된다.
--
-- ★ 정작 V4 가 "캘린더 기간 겹침 전용" 이라고 적어 둔 idx_articles_period_gist 는
--   쓸 수가 없다. period 는 두 날짜가 모두 있을 때만 만들어지는데(V2 의 CASE 가드),
--   캘린더는 "상시 모집, 12월 마감" 처럼 한쪽만 있는 공지를 반드시 포함해야 하기 때문이다.
--   그래서 그 인덱스는 아무도 쓰지 않는 채로 쓰기 비용만 내고 있었다.
--   (앱은 period 컬럼을 매핑조차 하지 않는다 — Article.java 주석 참조)
--
-- 여기서는 가드 없는 daterange 식에 GiST 를 건다.
-- daterange 는 NULL 경계를 무한으로 다루므로, 위 세 줄짜리 조건이
-- 연산자 하나로 정확히 같은 뜻이 된다.
--
--   daterange(starts_on, ends_on, '[]') && daterange(:monthStart, :monthEnd, '[]')
--
-- 두 날짜가 다 NULL 이면 (-무한, 무한) 이 되어 모든 달에 걸리므로
-- 부분 인덱스 조건으로 그런 행을 아예 제외한다. 쿼리도 같은 조건을 함께 실어
-- 플래너가 이 부분 인덱스를 고를 수 있게 한다.
--
-- starts_on > ends_on 이면 daterange 가 예외를 던지지만
-- ck_articles_period_order 가 그 조합을 이미 막고 있다.


-- 아무도 쓰지 않는 인덱스. 쓰기마다 갱신 비용만 낸다.
DROP INDEX IF EXISTS idx_articles_period_gist;


CREATE INDEX idx_articles_calendar
    ON articles USING GIST (daterange(starts_on, ends_on, '[]'))
    WHERE status = 'PUBLISHED'
      AND (starts_on IS NOT NULL OR ends_on IS NOT NULL);

COMMENT ON INDEX idx_articles_calendar IS
    'CAL-01 월간 겹침. period 컬럼과 달리 한쪽 날짜만 있는 공지도 담는다';
