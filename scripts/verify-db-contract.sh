#!/usr/bin/env bash
# DB 계약 검증. 스키마·권한·마스터데이터와 크롤러 권한 경계를 확인합니다.
#
#   읽기 검사        ./scripts/verify-db-contract.sh
#   쓰기 검사 포함   ./scripts/verify-db-contract.sh --with-write-tests
#
# 필요한 환경변수: PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
#                  (쓰기 검사에는 CRAWLER_PASSWORD 도 필요)
#
# ★ 쓰기 검사는 기본으로 꺼져 있습니다.
#   트랜잭션을 ROLLBACK 으로 끝내 아무것도 남지 않지만, 시퀀스는 전진하고
#   트리거도 실제로 돕니다. 운영 DB 에서는 켜지 마세요.
#
# 출력은 PASS/FAIL 과 개수뿐입니다 — 행 내용·권한 원문·비밀값은 찍지 않습니다.
set -uo pipefail

WRITE_TESTS=0
[ "${1:-}" = "--with-write-tests" ] && WRITE_TESTS=1

FAILED=0
psq() { psql -qtAX -v ON_ERROR_STOP=1 "$@"; }

check() {   # check <이름> <기대값> <실제값>
    if [ "$2" = "$3" ]; then
        printf '  PASS  %-42s %s\n' "$1" "$3"
    else
        printf '  FAIL  %-42s 기대=%s 실제=%s\n' "$1" "$2" "$3"
        FAILED=$((FAILED + 1))
    fi
}

echo "── 스키마 ─────────────────────────────────────────"

check "Flyway 최신 버전" "15" \
    "$(psq -c "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"

check "Flyway 실패 건수" "0" \
    "$(psq -c "SELECT count(*) FROM flyway_schema_history WHERE success = false")"

check "pg_bigm 확장" "1" \
    "$(psq -c "SELECT count(*) FROM pg_extension WHERE extname = 'pg_bigm'")"

check "트리거 20개 이상" "t" \
    "$(psq -c "SELECT count(*) >= 20 FROM pg_trigger WHERE NOT tgisinternal")"

echo "── 크롤러 권한 ────────────────────────────────────"

check "inform_crawler 롤" "1" \
    "$(psq -c "SELECT count(*) FROM pg_roles WHERE rolname = 'inform_crawler'")"

# ★ 개수가 아니라 목록을 비교합니다. 개수만 세면 엉뚱한 컬럼이 들어와도 통과합니다.
check "articles UPDATE 허용 컬럼" \
    "content,ends_on,similar_article_id,similarity_score,starts_on,title,version" \
    "$(psq -c "SELECT string_agg(column_name, ',' ORDER BY column_name)
                 FROM information_schema.column_privileges
                WHERE grantee = 'inform_crawler'
                  AND table_name = 'articles' AND privilege_type = 'UPDATE'")"

check "articles 테이블 UPDATE 미부여" "0" \
    "$(psq -c "SELECT count(*) FROM information_schema.table_privileges
                WHERE grantee = 'inform_crawler'
                  AND table_name = 'articles' AND privilege_type = 'UPDATE'")"

echo "── 마스터 데이터 ──────────────────────────────────"

check "선택 가능 분류" "11" \
    "$(psq -c "SELECT count(*) FROM categories WHERE is_active AND is_selectable")"

check "기타(ETC) 활성·선택불가" "t" \
    "$(psq -c "SELECT is_active AND NOT is_selectable FROM categories WHERE code = 'ETC'")"

check "동아리 유형" "8" \
    "$(psq -c "SELECT count(*) FROM club_types WHERE is_active")"

check "학과·기관 제공처" "88" \
    "$(psq -c "SELECT count(*) FROM vendors WHERE is_active AND type = 'SCHOOL'")"

if [ "$WRITE_TESTS" = "1" ]; then
    echo "── 크롤러 동작 (ROLLBACK 으로 끝납니다) ───────────"

    : "${CRAWLER_PASSWORD:?쓰기 검사에는 CRAWLER_PASSWORD 가 필요합니다}"
    crawler() { PGPASSWORD="$CRAWLER_PASSWORD" psql -qtAX -U inform_crawler "$@"; }

    # 1. status 를 바꿀 수 없어야 합니다. 이 경계가 "크롤러는 발행할 수 없다" 입니다.
    if crawler -c "UPDATE articles SET status = 'PUBLISHED' WHERE id = -1" 2>&1 \
        | grep -q 'permission denied'; then
        printf '  PASS  %-42s %s\n' "status 변경 거부" "permission denied"
    else
        printf '  FAIL  %-42s %s\n' "status 변경 거부" "거부되지 않음"
        FAILED=$((FAILED + 1))
    fi

    # 2~4. 트리거 동작. 한 트랜잭션에서 만들고 확인하고 되돌립니다.
    RESULT=$(crawler <<'SQL' 2>&1
BEGIN;
INSERT INTO articles (vendor_id, title, content, source_type, source_url, status)
SELECT id, 'contract-check', 'before', 'SCHOOL',
       'https://example.invalid/contract-check', 'PENDING_REVIEW'
  FROM vendors WHERE is_active LIMIT 1;

-- 본문을 바꾸면 트리거가 version 을 올려야 합니다(V7).
UPDATE articles SET content = 'after' WHERE title = 'contract-check';
SELECT 'version_bumped=' || (version > 0)::text
  FROM articles WHERE title = 'contract-check';

-- EXTERNAL 첨부는 object_key 없이 들어가야 합니다.
INSERT INTO attachments (article_id, original_name, file_url, storage_type, sort_order)
SELECT id, 'a.pdf', 'https://example.invalid/a.pdf', 'EXTERNAL', 0
  FROM articles WHERE title = 'contract-check';
SELECT 'external_ok=true';
ROLLBACK;
SQL
    )
    for expect in "version_bumped=true" "external_ok=true"; do
        if printf '%s' "$RESULT" | grep -q "$expect"; then
            printf '  PASS  %-42s %s\n' "${expect%%=*}" "${expect##*=}"
        else
            printf '  FAIL  %-42s %s\n' "${expect%%=*}" "확인 실패"
            FAILED=$((FAILED + 1))
        fi
    done
fi

echo "───────────────────────────────────────────────────"
if [ "$FAILED" -eq 0 ]; then
    echo "전부 통과"
else
    echo "실패 ${FAILED}건"
fi
exit "$FAILED"
