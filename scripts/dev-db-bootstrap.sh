#!/usr/bin/env bash
#
# 임시 dev DB 부트스트랩 — DEV_DB_BOOTSTRAP.md rev.7 의 전 과정을 한 번에 수행합니다.
#
#   sudo bash scripts/dev-db-bootstrap.sh <인스턴스ID> <Secret ARN>
#
# 대상 확인 → 저장소 → 설정 → 빌드 → Flyway → 계약 검증 22개
#   → 바인딩 확인 → Secret 저장 → 기준 행 수 → 회신 블록 출력
#
# ── 안전장치 ──────────────────────────────────────────────────────────────
#
# ★ 모든 단계 전에 인스턴스 ID 를 다시 확인합니다.
#   한 번만 확인하고 넘어갔다가 실제로 운영 서버에서 절차를 시작한 적이 있습니다.
#   세션은 바뀔 수 있고, 바뀐 걸 알려 주는 건 아무것도 없습니다.
#
# ★ 컨테이너·볼륨이 하나라도 있으면 중단합니다. 지워서 맞추지 않습니다.
#   남아 있다는 건 "새 빈 대상" 이 아니라는 뜻이고, 지우는 판단은 이 스크립트 몫이 아닙니다.
#
# ★ 비밀번호는 이 호스트에서 만들어 이 호스트 안에서만 씁니다.
#   화면·명령행 인자·셸 이력·디스크 임시파일 어디에도 남지 않습니다.
#   Secret 저장은 환경변수 → 표준입력 경로만 씁니다.
#
# ★ bash -x 로 실행하지 마세요. 비밀번호가 그대로 찍힙니다.
#
set -uo pipefail

if [ $# -ne 2 ]; then
    cat >&2 <<'USAGE'
사용법: sudo bash scripts/dev-db-bootstrap.sh <인스턴스ID> <Secret ARN>

  예) sudo bash scripts/dev-db-bootstrap.sh \
        i-0123456789abcdef0 \
        arn:aws:secretsmanager:ap-northeast-2:111122223333:secret:inform/crawler/dev/db-xxxx-AbCdEf

두 값 모두 인계 문서의 "이번 정확한 대상" 표에서 그대로 가져옵니다.
USAGE
    exit 2
fi

EXPECT_ID="$1"
SECRET_ARN="$2"

REPO_URL="https://github.com/Team-Alimi/Neo_IN-FORM_Backend.git"
TOOL_REV="6676ffb59283d9c9ea1fc37b606f6b45ce7ae028"
DB_REV="8a042da0ab66e91f83adeeef345fc95c88b08190"
WORK_DIR="${HOME}/inform-devdb"

# ── 회신 항목. 실패해도 지금까지의 결과를 그대로 내보냅니다 ──────────────
R_TARGET=FAILED
R_APPLIED=NOT_APPLIED
R_FLYWAY_EXIT=NOT_RUN
R_HISTORY=NOT_RUN
R_ROLE=NOT_RUN
R_WRITE=NOT_RUN
R_VERIFY_EXIT=NOT_RUN
R_BINDING=NOT_RUN
R_CRED=PENDING
R_PUT_EXIT=NOT_RUN
R_BASE_ART=NOT_RUN
R_BASE_AV=NOT_RUN
R_BASE_AC=NOT_RUN
R_BASE_ATT=NOT_RUN
R_BLOCKER=NONE

kst() { date -u -d '+9 hours' '+%Y-%m-%d %H:%M KST'; }
log() { printf '\n== %s ==\n' "$*"; }
ok()  { printf '   %s\n' "$*"; }

reply() {
    cat <<EOF

======== 회신 블록 (그대로 복사) ========
environment=nonproduction
target_instance_rechecked=${R_TARGET}
approved_db_revision=${DB_REV}
tool_source_revision=${TOOL_REV}
procedure_reference=DEV_DB_BOOTSTRAP.md rev.7
remote_target_application=${R_APPLIED}
flyway_exit_code=${R_FLYWAY_EXIT}
flyway_history_v1_through_v15=${R_HISTORY}
role_extension_grants_triggers_master_data=${R_ROLE}
contract_write_tests=${R_WRITE}
verify_exit_code=${R_VERIFY_EXIT}
private_only_binding=${R_BINDING}
credential_delivery=${R_CRED}
put_secret_value_exit_code=${R_PUT_EXIT}
crawler_private_connection=NOT_RUN
baseline_readonly_articles=${R_BASE_ART}
baseline_readonly_article_vendors=${R_BASE_AV}
baseline_readonly_article_categories=${R_BASE_AC}
baseline_readonly_attachments=${R_BASE_ATT}
baseline_readonly_article_status_logs=NOT_RUN
confirmed_at=$(kst)
blocker_or_exception=${R_BLOCKER}
=========================================
EOF
}

die() {
    R_BLOCKER="$*"
    printf '\n[중단] %s\n' "$*" >&2
    reply
    exit 1
}

imds() {
    local token
    token=$(curl -sfX PUT "http://169.254.169.254/latest/api/token" \
                 -H "X-aws-ec2-metadata-token-ttl-seconds: 60") || return 1
    curl -sf -H "X-aws-ec2-metadata-token: ${token}" \
         "http://169.254.169.254/latest/meta-data/$1"
}

# ★ 각 단계 직전에 부릅니다. 이전 확인을 재사용하지 않습니다.
guard() {
    local id
    id=$(imds instance-id) \
        || die "인스턴스 메타데이터를 읽을 수 없습니다. EC2 가 아니거나 IMDSv2 가 막혀 있습니다."
    [ "${id}" = "${EXPECT_ID}" ] \
        || die "대상이 아닙니다. 기대=${EXPECT_ID} 실제=${id}"
}

# ==========================================================================
log "1. 대상 확인"
guard
R_TARGET=CONFIRMED
ok "인스턴스 ${EXPECT_ID}"
ok "cloud-init $(cloud-init status 2>/dev/null | head -1 || echo '(확인 불가)')"
ok "종료 타이머 $(systemctl is-active inform-dev-db-expiry.timer 2>/dev/null || echo '(없음)')"
systemctl show inform-dev-db-expiry.timer -p NextElapseUSecRealtime 2>/dev/null | sed 's/^/   /'
ok "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null) / Compose $(docker compose version --short 2>/dev/null)"

[ -z "$(docker ps -aq 2>/dev/null)" ] \
    || die "기존 컨테이너가 있습니다. 빈 대상이 아니므로 중단합니다."
[ -z "$(docker volume ls -q 2>/dev/null)" ] \
    || die "기존 볼륨이 있습니다. 빈 대상이 아니므로 중단합니다."
[ ! -e "${WORK_DIR}" ] \
    || die "${WORK_DIR} 가 이미 있습니다. 이전 실행 흔적이므로 중단합니다."
ok "컨테이너·볼륨 없음"

# ==========================================================================
log "2. 저장소 받기"
guard
if ! command -v git >/dev/null 2>&1; then
    ok "git 설치 중"
    dnf install -y -q git-core >/dev/null 2>&1 || die "git 설치 실패"
fi
GIT_TERMINAL_PROMPT=0 git clone -q "${REPO_URL}" "${WORK_DIR}" || die "저장소 clone 실패"
cd "${WORK_DIR}" || die "작업 디렉토리 진입 실패"
git checkout -q "${TOOL_REV}" || die "도구 revision checkout 실패"
ok "받음 $(git rev-parse --short HEAD)"

# ==========================================================================
log "3. 설정 파일"
guard
PRIV=$(imds local-ipv4) || die "사설 IP 조회 실패"
[ -n "${PRIV}" ] || die "사설 IP 가 비어 있습니다"

# ★ base64 출력에는 따옴표·백슬래시가 없습니다.
#   initdb 가 비밀번호를 SQL 문자열에 그대로 끼워 넣기 때문에 이게 중요합니다.
umask 077
{
    echo "DB_NAME=inform"
    echo "DB_USER=inform"
    echo "DB_PASSWORD=$(openssl rand -base64 24)"
    echo "CRAWLER_PASSWORD=$(openssl rand -base64 24)"
    echo "DB_BIND_IP=${PRIV}"
    echo "DB_PORT=5432"
} > .env.dev-db || die ".env.dev-db 작성 실패"
ok "작성 완료 (권한 $(stat -c %a .env.dev-db), 값은 출력하지 않습니다)"

# 값은 환경변수로만 흐릅니다. 명령행에 실리지 않습니다.
set -a
# shellcheck disable=SC1091
. ./.env.dev-db
set +a

DC=(docker compose -f docker-compose.dev-db.yml --env-file .env.dev-db)

# ==========================================================================
log "4. 이미지 빌드 (4~6분)"
guard
# ★ compose 의 빌드 경로는 최신 buildx 를 요구하는데 이 호스트들에는 없습니다.
#   docker build 를 직접 쓰면 그 요구를 우회합니다.
#   compose 가 찾는 이름 그대로 태그를 붙이는 것이 핵심입니다.
PROJECT=$(basename "${WORK_DIR}")
if ! DOCKER_BUILDKIT=0 docker build \
        -t "${PROJECT}-db" -t "${PROJECT}-verify" \
        ./docker/db > /tmp/devdb-build.log 2>&1; then
    tail -20 /tmp/devdb-build.log >&2
    die "이미지 빌드 실패 (/tmp/devdb-build.log)"
fi
ok "완료"

# ==========================================================================
log "5. 기동 + Flyway"
guard
if ! "${DC[@]}" up -d > /tmp/devdb-up.log 2>&1; then
    tail -20 /tmp/devdb-up.log >&2
    die "컨테이너 기동 실패 (/tmp/devdb-up.log)"
fi

# ★ wait 가 Flyway 종료까지 막고 있다가 그 종료 코드를 그대로 돌려줍니다.
#   DB 가 healthy 한 것만으로는 마이그레이션 성공을 판정하지 않습니다.
"${DC[@]}" wait flyway
R_FLYWAY_EXIT=$?
ok "flyway 종료 코드 ${R_FLYWAY_EXIT}"
[ "${R_FLYWAY_EXIT}" -eq 0 ] || die "Flyway 실패"
R_APPLIED=APPLIED

# ==========================================================================
log "6. initdb 확인"
guard
EXT=$("${DC[@]}" exec -T db psql -qtAX -U "${DB_USER}" -d "${DB_NAME}" \
        -c "SELECT count(*) FROM pg_extension WHERE extname = 'pg_bigm'" \
        2>/dev/null | tr -d '[:space:]')
ROLE=$("${DC[@]}" exec -T db psql -qtAX -U "${DB_USER}" -d "${DB_NAME}" \
        -c "SELECT count(*) FROM pg_roles WHERE rolname = 'inform_crawler'" \
        2>/dev/null | tr -d '[:space:]')
[ "${EXT}" = "1" ]  || die "pg_bigm 확장이 없습니다 — initdb 가 돌지 않았습니다"
[ "${ROLE}" = "1" ] || die "inform_crawler 롤이 없습니다 — initdb 가 돌지 않았습니다"
ok "pg_bigm·inform_crawler 확인"

# ==========================================================================
log "7. 계약 검증 22개 항목"
guard
"${DC[@]}" --profile verify run --rm verify --with-write-tests
R_VERIFY_EXIT=$?
if [ "${R_VERIFY_EXIT}" -eq 0 ]; then
    R_HISTORY=PASS
    R_ROLE=PASS
    R_WRITE=PASS
else
    R_HISTORY=FAIL
    R_ROLE=FAIL
    R_WRITE=FAIL
    die "계약 검증 실패 (종료 코드 ${R_VERIFY_EXIT})"
fi

# ==========================================================================
log "8. 리스닝 주소"
guard
BIND=$(ss -lntH "( sport = :5432 )" 2>/dev/null | awk '{print $4}' | head -1)
case "${BIND}" in
    "")                           R_BINDING=NOT_RUN ;;
    0.0.0.0:5432|"[::]:5432")     R_BINDING=FAILED ;;
    127.0.0.1:5432|"[::1]:5432")  R_BINDING=FAILED ;;
    *:5432)                       R_BINDING=CONFIRMED ;;
    *)                            R_BINDING=FAILED ;;
esac
[ "${R_BINDING}" = "CONFIRMED" ] \
    || die "사설 주소 단독 바인딩이 아닙니다 (관측: ${BIND:-없음})"
ok "사설 주소 단독"

# ==========================================================================
log "9. 자격증명 저장"
guard
# ★ 값이 argv 에 들어가지 않습니다 — jq 는 환경변수에서, aws 는 표준입력에서 읽습니다.
jq -n '{host:$ENV.DB_BIND_IP, port:"5432", dbname:$ENV.DB_NAME, username:"inform_crawler", password:$ENV.CRAWLER_PASSWORD}' \
  | aws secretsmanager put-secret-value \
        --secret-id "${SECRET_ARN}" \
        --secret-string file:///dev/stdin \
        --query VersionId --output text 2>/tmp/devdb-put.err
R_PUT_EXIT=$?

if [ "${R_PUT_EXIT}" -eq 0 ]; then
    R_CRED=CONFIRMED
    ok "저장 완료 (종료 코드 0)"
else
    R_CRED=FAILED
    ERRKIND=$(grep -oE '[A-Za-z]+Exception|AccessDenied[A-Za-z]*' /tmp/devdb-put.err 2>/dev/null | head -1)
    die "Secret 저장 실패: ${ERRKIND:-원인 미상}. 권한을 임의로 넓히지 말고 운영 측에 알리세요."
fi

# ==========================================================================
log "10. smoke 전 기준 행 수 (inform_crawler, 읽기 전용)"
guard
# ★ article_status_logs 는 이 역할에 SELECT 권한이 없는 감사 테이블입니다.
#   조회하지 않고 권한도 추가하지 않습니다 — 회신에서 NOT_RUN 입니다.
#   비밀번호는 컨테이너 안 환경변수에서 읽습니다. 바깥 명령행에 실리지 않습니다.
BASE=$("${DC[@]}" exec -T db sh -c 'PGPASSWORD="$CRAWLER_PASSWORD" psql -qtAX -F" " -U inform_crawler -d "$POSTGRES_DB" -c "BEGIN READ ONLY; SELECT (SELECT count(*) FROM articles), (SELECT count(*) FROM article_vendors), (SELECT count(*) FROM article_categories), (SELECT count(*) FROM attachments); COMMIT;"' 2>/dev/null \
        | tr -d '\r' | grep -E '^[0-9]+ [0-9]+ [0-9]+ [0-9]+$' | head -1)

[ -n "${BASE}" ] || die "기준 행 수 조회 실패"
read -r R_BASE_ART R_BASE_AV R_BASE_AC R_BASE_ATT <<< "${BASE}"
ok "articles=${R_BASE_ART} article_vendors=${R_BASE_AV} article_categories=${R_BASE_AC} attachments=${R_BASE_ATT}"

unset DB_PASSWORD CRAWLER_PASSWORD

# ==========================================================================
printf '\n[완료] 전부 통과\n'
reply
