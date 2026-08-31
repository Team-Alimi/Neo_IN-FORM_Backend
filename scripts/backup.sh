#!/bin/bash
#
# INFORM v2 DB 백업 — docs/BACKUP_POLICY.md §3.1 구현
#
#   설치:  sudo install -m 755 scripts/backup.sh /opt/inform/backup.sh
#   크론:  0 0,6,12,18 * * * /opt/inform/backup.sh >> /var/log/inform-backup.log 2>&1
#
# 정책 초안에서 고친 것 세 가지:
#
#  1) 컨테이너 이름이 inform-db 가 아니라 inform-db-1 입니다.
#     compose 가 프로젝트명(inform) + 서비스명(db) + 인덱스(1) 로 짓습니다.
#     초안 그대로 두면 "No such container" 로 매번 조용히 실패합니다.
#
#  2) HEALTHCHECK_URL 이 정의돼 있지 않은데 set -u 상태에서 참조합니다.
#     덤프가 성공해도 마지막 줄에서 unbound variable 로 죽어 실패로 기록됩니다.
#     설정했을 때만 호출하도록 바꿨습니다.
#
#  3) 초안은 pg_dump 출력을 S3 로 바로 파이프합니다. 도중에 pg_dump 가 죽으면
#     잘린 덤프가 이미 S3 에 올라간 뒤라, 복원을 시도할 때까지 아무도 모릅니다.
#     임시 파일에 받아 크기를 확인한 뒤 올립니다.

set -euo pipefail

BUCKET="s3://inform-backup"
CONTAINER="inform-db-1"
DB="inform"
DB_USER="inform"
TMPDIR_BASE="/var/tmp"

# 설정하면 성공 시 이 URL 을 호출합니다 (healthchecks.io 등).
# 안 설정하면 건너뜁니다 — 크론은 조용히 실패하므로 운영에서는 설정하세요.
HEALTHCHECK_URL="${HEALTHCHECK_URL:-}"

TS=$(date +%Y%m%d-%H%M%S)
log() { echo "[$(date '+%F %T')] $*"; }

log "백업 시작 ($TS)"

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    log "실패: 컨테이너 '$CONTAINER' 가 없습니다."
    docker ps --format '  {{.Names}}' >&2
    exit 1
fi

WORK=$(mktemp -d "$TMPDIR_BASE/inform-backup.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

# ── 1) 스키마 + 데이터 ────────────────────────────────────────────────
# ★ docker exec 에 -t 를 붙이면 안 됩니다. tty 가 출력에 CR 을 섞어
#   바이너리 덤프가 손상되고, 복원을 시도할 때까지 발견되지 않습니다.
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB" -Fc > "$WORK/db.dump"

SIZE=$(stat -c%s "$WORK/db.dump")
if [ "$SIZE" -lt 1024 ]; then
    log "실패: 덤프가 너무 작습니다 (${SIZE}B). 올리지 않습니다."
    exit 1
fi
log "덤프 생성 ${SIZE}B"

# ── 2) 롤·권한 등 클러스터 전역 객체 ──────────────────────────────────
# pg_dump 는 DB 하나만 덤프합니다. 롤이 없으면 복원 시 GRANT 가 전부 실패합니다.
docker exec "$CONTAINER" pg_dumpall -U "$DB_USER" --globals-only \
    | gzip > "$WORK/globals.sql.gz"

# ── 3) 업로드 ─────────────────────────────────────────────────────────
aws s3 cp "$WORK/db.dump"        "$BUCKET/db/inform-$TS.dump"
aws s3 cp "$WORK/globals.sql.gz" "$BUCKET/globals/globals-$TS.sql.gz"

log "업로드 완료: $BUCKET/db/inform-$TS.dump"

# ── 4) 성공 신호 ──────────────────────────────────────────────────────
if [ -n "$HEALTHCHECK_URL" ]; then
    curl -fsS -m 10 "$HEALTHCHECK_URL" > /dev/null && log "헬스체크 통보 완료"
fi

log "백업 종료"
