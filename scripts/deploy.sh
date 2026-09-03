#!/bin/bash
#
# 서버 배포 — GitHub Actions 가 SSM Run Command 로 호출합니다.
#
#   사용:  bash scripts/deploy.sh ghcr.io/team-alimi/inform-app:<태그>
#
# ★ 이 스크립트는 ssm-user 로 실행되어야 합니다.
#   SSM Run Command 자체는 root 로 도는데, root 가 ssm-user 소유의 git 저장소를
#   건드리면 git 이 "detected dubious ownership" 으로 거부하고,
#   docker 가 만드는 파일 소유자도 뒤섞입니다.
#   그래서 워크플로가 sudo -u ssm-user 로 감싸서 호출합니다.

set -euo pipefail

REPO_DIR="${REPO_DIR:-/home/ssm-user/inform}"
COMPOSE="docker compose -f $REPO_DIR/docker-compose.prod.yml"
HEALTH_URL="http://localhost:8080/actuator/health"
HEALTH_TIMEOUT=90     # 초. Flyway 마이그레이션이 있으면 기동이 느려집니다.

NEW_IMAGE="${1:-}"
[ -n "$NEW_IMAGE" ] || { echo "사용법: deploy.sh <이미지>"; exit 1; }

log() { echo "[$(date '+%F %T')] $*"; }
cd "$REPO_DIR"

# ── 현재 이미지 기억 (롤백용) ────────────────────────────────────────
PREV_IMAGE=$(sed -n 's/^APP_IMAGE=//p' .env)
log "현재: ${PREV_IMAGE:-(없음)}"
log "신규: $NEW_IMAGE"

if [ "$PREV_IMAGE" = "$NEW_IMAGE" ]; then
    log "같은 이미지입니다. 그래도 재기동합니다."
fi

# ── .env 의 APP_IMAGE 교체 ───────────────────────────────────────────
# .env 전체를 건드리지 않고 이 한 줄만 바꿉니다. 다른 비밀값은 그대로 둡니다.
sed -i "s|^APP_IMAGE=.*|APP_IMAGE=$NEW_IMAGE|" .env

rollback() {
    log "롤백: $PREV_IMAGE 로 되돌립니다"
    sed -i "s|^APP_IMAGE=.*|APP_IMAGE=$PREV_IMAGE|" "$REPO_DIR/.env"
    $COMPOSE up -d app || true
    log "★ 주의: 이미지는 되돌렸지만 DB 스키마는 되돌아가지 않습니다."
    log "  Flyway 마이그레이션이 이미 적용됐다면 백업에서 복원해야 합니다."
}

# ── 새 이미지 받기 ───────────────────────────────────────────────────
if ! $COMPOSE pull app; then
    log "pull 실패. 배포를 중단하고 .env 를 되돌립니다."
    sed -i "s|^APP_IMAGE=.*|APP_IMAGE=$PREV_IMAGE|" .env
    exit 1
fi

# ── 교체 ─────────────────────────────────────────────────────────────
log "앱 교체 중..."
$COMPOSE up -d app

# ── 건강 확인 ────────────────────────────────────────────────────────
# 컨테이너가 Up 인 것만으로는 부족합니다. Flyway 실패나 빈 설정값은
# 컨테이너가 뜬 뒤에 드러납니다.
log "health 확인 (최대 ${HEALTH_TIMEOUT}초)"
DEADLINE=$(( $(date +%s) + HEALTH_TIMEOUT ))
OK=0
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    if curl -fsS -m 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
        OK=1; break
    fi
    sleep 3
done

if [ "$OK" -ne 1 ]; then
    log "health 실패 — 앱이 ${HEALTH_TIMEOUT}초 안에 정상이 되지 않았습니다."
    echo "── 앱 로그 (마지막 60줄) ──"
    $COMPOSE logs --tail=60 app || true
    [ -n "$PREV_IMAGE" ] && rollback
    exit 1
fi

log "health OK"

# ── 뒷정리 ───────────────────────────────────────────────────────────
# 디스크가 20GB 뿐이라 낡은 이미지가 쌓이면 금방 찹니다.
# dangling 만 지웁니다 — 이전 태그는 롤백용으로 남겨둡니다.
docker image prune -f >/dev/null 2>&1 || true

log "배포 완료: $NEW_IMAGE"
$COMPOSE ps app
