-- 공지사항: 대표 이미지 한 장 (2026-10-08 프론트 요청)
--
-- V16 에서 "팝업에 이미지 안 들어감" 으로 정했던 것을 되돌린다.
-- V16 에 합치지 않고 새 마이그레이션으로 두는 이유 — V16 은 이미 커밋돼서
-- 다른 사람이 받아 간 뒤일 수 있다. 적용된 마이그레이션을 고치면 Flyway 가
-- 체크섬 불일치로 기동을 거부한다.
--
-- ★ 왜 attachments 를 쓰지 않는가
--   attachments.article_id 가 NOT NULL 이고 articles 를 참조한다. 공지(announcements)를
--   넣을 자리가 없다. 테이블을 공용으로 바꾸려면 ON DELETE CASCADE, 공지 단위 유니크,
--   교차 검증 트리거가 전부 articles 전제라 같이 뒤집어야 한다.
--   필요한 것은 "대표 이미지 한 장" 이라 목록 구조 자체가 필요 없다.
--
-- ★ 컬럼 하나만 둔다 (object_key 를 따로 두지 않는다)
--   attachments 는 file_url 과 object_key 를 함께 저장하지만, 그건 EXTERNAL(원본 사이트)
--   첨부가 섞여 key 가 없는 행이 존재하기 때문이다. 공지 이미지는 반드시 우리 스토리지
--   주소만 받으므로(서비스가 FileStorage.objectKeyOf 로 검증) URL 에서 key 를 되짚을 수 있다.
--
-- ★ 이 컬럼이 생기면 파일 삭제 보호를 함께 넓혀야 한다
--   DELETE /admin/files 는 "공지에 연결됐는가" 를 attachments 만 보고 판정한다.
--   그대로 두면 운영 중인 공지의 이미지를 그 API 로 지울 수 있고, 오류 없이
--   이미지만 안 뜨는 상태가 된다. AttachmentQueryRepository.findLinkedUrls 가
--   이 컬럼까지 UNION 으로 본다 — 같은 커밋에 들어 있다.

ALTER TABLE announcements ADD COLUMN image_url varchar(1000);

COMMENT ON COLUMN announcements.image_url IS
    '대표 이미지. POST /admin/files 가 돌려준 우리 스토리지 URL 만 들어간다. NULL = 이미지 없음';


-- =============================================================================
-- 검증
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'announcements' AND column_name = 'image_url'
    ) THEN
        RAISE EXCEPTION 'announcements.image_url 이 없습니다 — ADD COLUMN 이 적용되지 않았습니다';
    END IF;

    -- 기존 행이 있다면 전부 NULL 이어야 한다(이미지 없음). NOT NULL DEFAULT 로 잘못 추가하면
    -- 모든 공지에 빈 문자열이 들어가고, 화면은 "이미지 있음" 으로 읽어 깨진 이미지를 그린다.
    IF EXISTS (SELECT 1 FROM announcements WHERE image_url IS NOT NULL) THEN
        RAISE EXCEPTION 'image_url 이 채워진 행이 있습니다 — 새 컬럼인데 값이 들어갔습니다';
    END IF;
END $$;
