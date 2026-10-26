-- NTF-05 마감 알림 이메일 발송 (2026-10-25)
--
-- 인앱 알림은 uk_notifications_dedup + ON CONFLICT DO NOTHING 으로 멱등하지만,
-- 메일은 외부 부작용이라 되돌릴 수 없다. 배치가 두 번 돌면 같은 메일이 두 번 간다.
-- 그래서 "이 알림의 메일을 보냈는가" 를 행에 남긴다.
--
-- email_sent_at IS NULL 인 것만 골라 보내고, 성공하면 찍는다.
-- 이 한 컬럼이 중복 방지와 재시도를 동시에 해결한다 —
-- 발송 중 실패하면 NULL 로 남아 다음 주기에 자연히 재시도된다.

ALTER TABLE notifications ADD COLUMN email_sent_at timestamptz;

COMMENT ON COLUMN notifications.email_sent_at IS
    '메일 발송 완료 시각. NULL = 아직 안 보냄(발송 대상). NTF-05';


-- =============================================================================
-- ★ 기존 알림 백필 — 이게 없으면 첫 배포에 지난 알림이 메일로 쏟아진다
-- =============================================================================
-- 컬럼을 추가하면 기존 행이 전부 NULL 이 되고, 발송 배치는 그것을 "아직 안 보냄" 으로
-- 읽는다. 그러면 이미 마감이 지난 공지의 "마감이 하루 남았습니다" 메일이
-- 사용자에게 한꺼번에 날아간다. 되돌릴 수 없는 종류의 사고다.
--
-- 그래서 V18 이전에 만들어진 알림은 "발송 대상 아님" 으로 닫아 둔다.
-- 시각에 created_at 이 아니라 now() 를 쓰는 이유 — 실제로 메일을 보낸 적이 없으므로
-- "그때 보냈다" 고 적으면 거짓이 된다. "이 마이그레이션 시점에 대상에서 제외했다" 가 사실이다.
UPDATE notifications SET email_sent_at = now() WHERE email_sent_at IS NULL;


-- =============================================================================
-- 인덱스
-- =============================================================================
-- 발송 대기 행만 찾는 부분 인덱스. 보낸 행은 인덱스에서 빠지므로
-- 알림이 쌓여도 이 인덱스는 "아직 안 보낸 것" 크기로만 유지된다.
--
-- ★ created_at 을 선두에 두는 이유 — 발송 배치는 "안 보낸 것" 전부가 아니라
--   "최근에 만들어졌고 안 보낸 것" 만 고른다(inform.notification.email.max-age).
--
--   왜 기간 창이 필요한가: 메일 수신을 꺼 둔 사용자의 알림은 영원히 email_sent_at
--   이 NULL 로 남는다. 창이 없으면 그 사용자가 나중에 토글을 켜는 순간 지난 30일치
--   (SYS-04 보관 기간)가 한꺼번에 메일로 날아간다. V18 백필로 막은 것과 같은 종류의
--   소급 발송 사고다. 창을 두면 그 행들은 조용히 창 밖으로 밀려 대상에서 빠진다.
--
--   그래서 배치의 WHERE 는 (email_sent_at IS NULL AND created_at > ?) 이고,
--   인덱스 선두가 created_at 이어야 그 범위 조건이 인덱스를 탄다.
--   id 는 tie-breaker 다 — 같은 배치에서 만들어진 알림은 created_at 이 전부 같다.
CREATE INDEX idx_notifications_email_pending ON notifications (created_at, id)
    WHERE email_sent_at IS NULL;


-- =============================================================================
-- 검증
-- =============================================================================
DO $$
DECLARE
    pending bigint;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'notifications' AND column_name = 'email_sent_at'
    ) THEN
        RAISE EXCEPTION 'notifications.email_sent_at 이 없습니다 — ADD COLUMN 이 적용되지 않았습니다';
    END IF;

    -- 백필이 빠지면 여기서 걸린다. 0 이 아니면 소급 발송 위험이 남아 있다는 뜻이다.
    SELECT count(*) INTO pending FROM notifications WHERE email_sent_at IS NULL;
    IF pending > 0 THEN
        RAISE EXCEPTION '백필되지 않은 알림이 % 건 남아 있습니다 — 첫 배치가 소급 발송합니다', pending;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE tablename = 'notifications' AND indexname = 'idx_notifications_email_pending'
    ) THEN
        RAISE EXCEPTION 'idx_notifications_email_pending 이 없습니다';
    END IF;
END $$;
