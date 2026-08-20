-- =============================================================================
-- V8. comments 에 낙관적 잠금 컬럼 추가
-- =============================================================================
--
-- 댓글 수정과 삭제가 서로를 덮어쓰는 경로가 있었다.
--
--   T1  PATCH /comments/42  → 엔티티 로드 (deleted_at = NULL)
--   T2  DELETE /comments/42 → 답글이 있어 soft delete. deleted_at = now(), content = ''
--                             커밋. comment_count -1
--   T1  커밋 → UPDATE comments SET content = ?, deleted_at = ? WHERE id = 42
--             deleted_at 자리에 로드 시점의 NULL 이 실려 나간다
--
-- 결과적으로 사용자가 지운 댓글이 새 본문을 달고 목록에 다시 나타난다.
-- 갱신 행 수가 1이라 예외도 나지 않고, comment_count 트리거가 다시 +1 을 돌려놓아
-- 개수마저 앞뒤가 맞는다. 이상을 눈치챌 단서가 하나도 없다.
--
-- ★ 하드 삭제였다면 0행 갱신 → 낙관적 잠금 예외 → 409 로 잘 나간다.
--   하필 "자리를 남기는" 경로에서만 조용히 깨진다.
--
-- version 을 두면 T1 의 WHERE 에 version 조건이 붙어 0행이 되고,
-- GlobalExceptionHandler 가 이미 CONCURRENT_MODIFICATION(409) 으로 옮긴다.
--
-- articles.version 과 같은 형태로 맞춘다.

ALTER TABLE comments
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE comments
    ADD CONSTRAINT ck_comments_version CHECK (version >= 0);

COMMENT ON COLUMN comments.version IS
    'JPA @Version. 수정과 삭제가 서로를 덮어쓰는 것을 막는다';
