#!/usr/bin/env bash
# DB 계약 검증. 스키마·권한·마스터데이터와 크롤러 권한 경계를 확인합니다.
#
#   읽기 검사만      ./scripts/verify-db-contract.sh
#   쓰기 검사 포함   ./scripts/verify-db-contract.sh --with-write-tests
#
# 필요한 환경변수: PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
#                  (쓰기 검사에는 CRAWLER_PASSWORD 도 필요)
#
# 출력은 PASS/FAIL 과 비교값뿐입니다. 행 내용·권한 원문·비밀값은 찍지 않습니다.
# psql 의 stderr 도 버립니다 — 오류 메시지에 접속 정보가 섞여 나갈 수 있습니다.
# 그 대가로 실패 원인이 안 보이므로, 디버깅할 때는 PSQL_DEBUG=1 로 켜세요.
set -uo pipefail

WRITE_TESTS=0
case "${1:-}" in
    --with-write-tests) WRITE_TESTS=1 ;;
    "")                 ;;
    *) echo "사용법: $0 [--with-write-tests]" >&2; exit 2 ;;
esac

ERR=/dev/null
[ "${PSQL_DEBUG:-0}" = "1" ] && ERR=/dev/stderr

FAILED=0
psq() { psql -qtAX -v ON_ERROR_STOP=1 "$@" 2>"$ERR"; }

check() {   # check <이름> <기대값> <실제값>
    if [ "$2" = "$3" ]; then
        printf '  PASS  %-40s %s\n' "$1" "$3"
    else
        printf '  FAIL  %-40s 기대=%s 실제=%s\n' "$1" "$2" "${3:-(없음)}"
        FAILED=$((FAILED + 1))
    fi
}

echo "── 스키마 ───────────────────────────────────────"

# ★ 최신 버전만 보면 중간이 빠져도 통과합니다. 적용된 버전 전체를 목록으로 비교합니다.
check "적용된 마이그레이션 전체" "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15" \
    "$(psq -c "SELECT string_agg(version, ',' ORDER BY version::numeric)
                 FROM flyway_schema_history WHERE version IS NOT NULL")"

check "Flyway 실패 건수" "0" \
    "$(psq -c "SELECT count(*) FROM flyway_schema_history WHERE success = false")"

check "pg_bigm 확장" "1" \
    "$(psq -c "SELECT count(*) FROM pg_extension WHERE extname = 'pg_bigm'")"

# ★ 개수로 세면 엉뚱한 트리거가 들어와도 통과합니다. 이름으로 확인합니다.
for trg in trg_articles_10_immutable trg_articles_20_crawler_policy \
           trg_articles_25_published_at trg_articles_30_summary_invalidate \
           trg_articles_90_status_audit; do
    check "트리거 $trg" "1" \
        "$(psq -c "SELECT count(*) FROM pg_trigger
                    WHERE NOT tgisinternal AND tgname = '$trg'")"
done

echo "── 크롤러 권한 ──────────────────────────────────"

check "inform_crawler 롤" "1" \
    "$(psq -c "SELECT count(*) FROM pg_roles WHERE rolname = 'inform_crawler'")"

# ★ 개수가 아니라 목록을 통째로 비교합니다.
#   status·summary·조회수가 목록에 없다는 것이 곧 "크롤러는 발행할 수 없다" 입니다.
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

check "시퀀스 USAGE" "3" \
    "$(psq -c "SELECT count(*) FROM information_schema.usage_privileges
                WHERE grantee = 'inform_crawler' AND object_type = 'SEQUENCE'
                  AND object_name IN
                      ('articles_id_seq','article_vendors_id_seq','attachments_id_seq')")"

echo "── 마스터 데이터 ────────────────────────────────"

check "선택 가능 분류" "11" \
    "$(psq -c "SELECT count(*) FROM categories WHERE is_active AND is_selectable")"

check "기타(ETC) 활성·선택불가" "t" \
    "$(psq -c "SELECT is_active AND NOT is_selectable FROM categories WHERE code = 'ETC'")"

check "동아리 유형" "8" \
    "$(psq -c "SELECT count(*) FROM club_types WHERE is_active")"

# ★ V15 는 88개를 넣지만 운영에서는 더 늘어납니다. 하한으로 봅니다.
check "학과·기관 제공처 88개 이상" "t" \
    "$(psq -c "SELECT count(*) >= 88 FROM vendors WHERE is_active AND type = 'SCHOOL'")"

if [ "$WRITE_TESTS" = "1" ]; then
    echo "── 크롤러 동작 ──────────────────────────────────"
    echo "  ※ 합성 데이터를 넣었다가 ROLLBACK 합니다."
    echo "     행은 남지 않지만 시퀀스는 전진하고 트리거도 실제로 돕니다."
    echo "     운영 DB 에서는 실행하지 마세요."

    : "${CRAWLER_PASSWORD:?쓰기 검사에는 CRAWLER_PASSWORD 가 필요합니다}"
    crawler() { PGPASSWORD="$CRAWLER_PASSWORD" psql -qtAX -U inform_crawler "$@"; }

    # 1. status 를 바꿀 수 없어야 합니다. 이 경계가 "크롤러는 발행할 수 없다" 입니다.
    # ★ 출력을 변수에 먼저 담고 나서 검사합니다.
    #   `psql ... | grep -q` 로 쓰면 set -o pipefail 이 판정을 뒤집습니다 —
    #   거부가 정답인 검사라 psql 은 반드시 1 로 끝나는데, pipefail 이 그걸
    #   파이프라인 실패로 만들어 "거부되지 않음" 으로 보고합니다.
    #   권한이 멀쩡한데 구멍이 있다고 알리는, 최악의 방향으로 틀리는 검사였습니다.
    for col in "status = 'PUBLISHED'" "summary = 'x'"; do
        OUT=$(crawler -c "UPDATE articles SET $col WHERE id = -1" 2>&1)
        if printf '%s' "$OUT" | grep -q 'permission denied'; then
            printf '  PASS  %-40s %s\n' "${col%% *} 변경 거부" "permission denied"
        else
            printf '  FAIL  %-40s %s\n' "${col%% *} 변경 거부" "거부되지 않음"
            FAILED=$((FAILED + 1))
        fi
    done

    # 2. 4개 테이블 쓰기와 트리거 동작. 한 트랜잭션에서 만들고 확인하고 되돌립니다.
    #    ★ fixture 를 제목이 아니라 RETURNING 으로 받은 id 로 지목합니다.
    #      제목으로 찾으면 남아 있던 다른 테스트 행을 검사하게 됩니다.
    RESULT=$(crawler -v ON_ERROR_STOP=1 <<'SQL' 2>"$ERR"
BEGIN;

INSERT INTO articles (source_type, title, content, status)
VALUES ('SCHOOL', 'contract-check', 'before', 'READY_TO_PUBLISH')
RETURNING id AS aid \gset

INSERT INTO article_vendors (article_id, vendor_id, source_url, external_key)
SELECT :aid, id, 'https://example.invalid/contract-check', 'contract-check-key'
  FROM vendors WHERE is_active ORDER BY id LIMIT 1;

INSERT INTO article_categories (article_id, category_id)
SELECT :aid, id FROM categories WHERE is_active ORDER BY id LIMIT 1;

-- EXTERNAL 첨부는 object_key 없이 들어가야 합니다(크롤러는 S3 객체를 만들지 않습니다).
INSERT INTO attachments (article_id, file_url, storage_type, original_name, sort_order)
VALUES (:aid, 'https://example.invalid/a.pdf', 'EXTERNAL', 'a.pdf', 0);

SELECT 'four_tables=' || (
       (SELECT count(*) FROM article_vendors    WHERE article_id = :aid) = 1
   AND (SELECT count(*) FROM article_categories WHERE article_id = :aid) = 1
   AND (SELECT count(*) FROM attachments        WHERE article_id = :aid) = 1)::text;

SELECT version AS v_before FROM articles WHERE id = :aid \gset

-- 본문 변경 → 강등(PENDING_REVIEW) + version 자동 증가
UPDATE articles SET content = 'after' WHERE id = :aid;

SELECT 'demoted='        || (status = 'PENDING_REVIEW')::text FROM articles WHERE id = :aid;
SELECT 'version_bumped=' || (version > :v_before)::text       FROM articles WHERE id = :aid;

-- 휴지통에 있던 글은 강등되지 않고 TRASHED 로 남아야 합니다.
INSERT INTO articles (source_type, title, content, status)
VALUES ('SCHOOL', 'contract-check-trashed', 'before', 'TRASHED')
RETURNING id AS bid \gset

UPDATE articles SET content = 'after' WHERE id = :bid;
SELECT 'trashed_kept=' || (status = 'TRASHED')::text FROM articles WHERE id = :bid;

ROLLBACK;
SQL
    )

    for expect in four_tables demoted version_bumped trashed_kept; do
        if printf '%s' "$RESULT" | grep -qx "$expect=true"; then
            printf '  PASS  %-40s %s\n' "$expect" "true"
        else
            printf '  FAIL  %-40s %s\n' "$expect" "false 또는 실행 실패"
            FAILED=$((FAILED + 1))
        fi
    done
fi

echo "─────────────────────────────────────────────────"
if [ "$FAILED" -eq 0 ]; then
    echo "전부 통과"
else
    echo "실패 ${FAILED}건  (원인을 보려면 PSQL_DEBUG=1)"
fi
exit "$FAILED"
