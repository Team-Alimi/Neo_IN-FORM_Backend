#!/bin/bash
#
# 백업 복원 테스트 — docs/BACKUP_POLICY.md §4 를 "운영 서버에서 안전하게" 돌도록 고친 것
#
#   사용:  sudo /opt/inform/restore-test.sh            # S3 최신 백업으로
#          sudo /opt/inform/restore-test.sh <S3키>     # 특정 백업으로
#
# 정책 §4 를 그대로 쓰면 안 되는 이유:
#
#   정책의 절차는 "빈 클러스터에 복원" 즉 실제 재해 복구용입니다.
#   운영 서버에서 그대로 실행하면 pg_restore 가 운영 DB(inform)에 덮어씁니다.
#   그래서 여기서는 임시 DB 에 복원하고, 끝나면 지웁니다.
#
#   또 정책은 컨테이너를 inform-db, 슈퍼유저를 postgres 로 적었는데
#   실제로는 inform-db-1 이고 슈퍼유저는 inform 입니다
#   (postgres 이미지가 POSTGRES_USER 를 슈퍼유저로 만듭니다).
#
# 검증 방식: 고정된 기댓값(정책의 "트리거 25개")을 쓰지 않습니다.
#   스키마는 마이그레이션마다 바뀌므로 금방 낡습니다.
#   대신 복원본을 **운영 DB 와 직접 비교**합니다. 자기 교정이 됩니다.

set -euo pipefail

CONTAINER="inform-db-1"
SU="inform"                       # 이 클러스터의 슈퍼유저
PROD_DB="inform"
TEST_DB="inform_restore_test"     # ← 이 이름 외에는 절대 지우지 않습니다
BUCKET="s3://inform-backup"

log()  { echo "[$(date '+%F %T')] $*"; }
psql_() { docker exec -i "$CONTAINER" psql -U "$SU" -v ON_ERROR_STOP=1 "$@"; }
q()     { docker exec -i "$CONTAINER" psql -U "$SU" -d "$1" -tAc "$2"; }

# ── 안전장치 ─────────────────────────────────────────────────────────
if [ "$TEST_DB" = "$PROD_DB" ]; then
    echo "안전장치: 테스트 DB 이름이 운영과 같습니다. 중단."; exit 1
fi

docker inspect "$CONTAINER" >/dev/null 2>&1 || { log "컨테이너 $CONTAINER 없음"; exit 1; }

# ── 백업 내려받기 ────────────────────────────────────────────────────
KEY="${1:-}"
if [ -z "$KEY" ]; then
    KEY=$(aws s3 ls "$BUCKET/db/" | sort | tail -1 | awk '{print $4}')
    [ -n "$KEY" ] || { log "S3 에 백업이 없습니다"; exit 1; }
fi
log "대상 백업: $KEY"

WORK=$(mktemp -d /var/tmp/restore-test.XXXXXX)
trap 'rm -rf "$WORK"; docker exec -i "$CONTAINER" psql -U "'"$SU"'" -d postgres -c "DROP DATABASE IF EXISTS '"$TEST_DB"'" >/dev/null 2>&1 || true' EXIT

aws s3 cp "$BUCKET/db/$KEY" "$WORK/test.dump"
SIZE=$(stat -c%s "$WORK/test.dump")
log "내려받음 ${SIZE}B"

# ── 임시 DB 에 복원 ──────────────────────────────────────────────────
log "임시 DB 생성: $TEST_DB"
psql_ -d postgres -c "DROP DATABASE IF EXISTS $TEST_DB"
psql_ -d postgres -c "CREATE DATABASE $TEST_DB"

# pg_bigm 은 DB 단위 확장이고 trusted 가 아니라 슈퍼유저로 먼저 만들어야 합니다.
# 이게 없으면 덤프 안의 인덱스 정의가 전부 실패합니다.
psql_ -d "$TEST_DB" -c "CREATE EXTENSION IF NOT EXISTS pg_bigm"

log "복원 중..."
set +e
docker exec -i "$CONTAINER" pg_restore -U "$SU" -d "$TEST_DB" --no-owner \
    < "$WORK/test.dump" 2> "$WORK/restore.err"
RC=$?
set -e
ERRS=$(grep -ci "^pg_restore: error" "$WORK/restore.err" || true)
log "pg_restore 종료코드=$RC, 에러 ${ERRS}건"
[ "$ERRS" -gt 0 ] && { echo "--- 에러 앞부분 ---"; head -20 "$WORK/restore.err"; }

# ── 운영 DB 와 비교 ──────────────────────────────────────────────────
echo
printf '%-38s %12s %12s   %s\n' "항목" "운영" "복원본" "결과"
printf '%s\n' "------------------------------------------------------------------------------"

FAIL=0
cmp_() {   # 이름  SQL
    local name="$1" sql="$2" a b mark
    a=$(q "$PROD_DB" "$sql"); b=$(q "$TEST_DB" "$sql")
    if [ "$a" = "$b" ]; then mark="OK"; else mark="불일치"; FAIL=$((FAIL+1)); fi
    printf '%-38s %12s %12s   %s\n' "$name" "$a" "$b" "$mark"
}

cmp_ "테이블 수"        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"
cmp_ "트리거 수"        "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal"
cmp_ "인덱스 수"        "SELECT count(*) FROM pg_indexes WHERE schemaname='public'"
cmp_ "CHECK 제약 수"    "SELECT count(*) FROM pg_constraint WHERE contype='c'"
cmp_ "FK 제약 수"       "SELECT count(*) FROM pg_constraint WHERE contype='f'"
cmp_ "crawler 컬럼권한"  "SELECT count(*) FROM information_schema.column_privileges WHERE grantee='inform_crawler'"
cmp_ "Flyway 성공 건수"  "SELECT count(*) FROM flyway_schema_history WHERE success"
cmp_ "articles 행수"     "SELECT count(*) FROM articles"
cmp_ "users 행수"        "SELECT count(*) FROM users"

# 생성 컬럼이 실제로 계산되는지 — 복원본에서만 확인 (덤프에 값이 안 들어갑니다)
echo
GEN=$(q "$TEST_DB" "SELECT count(*) FILTER (WHERE period IS NOT NULL) || '/' || count(*) FROM articles" 2>/dev/null || echo "확인불가")
echo "생성 컬럼 period 계산됨 : $GEN"

# pg_bigm 인덱스가 실제로 쓰이는지
BIGM=$(q "$TEST_DB" "SELECT extname FROM pg_extension WHERE extname='pg_bigm'")
echo "pg_bigm 확장            : ${BIGM:-없음}"

echo
if [ "$FAIL" -eq 0 ] && [ "$ERRS" -eq 0 ]; then
    echo "복원 테스트 통과 — 이 백업은 복원 가능합니다."
else
    echo "복원 테스트 실패 — 불일치 ${FAIL}건, 복원 에러 ${ERRS}건. 백업을 신뢰하지 마세요."
    exit 1
fi
