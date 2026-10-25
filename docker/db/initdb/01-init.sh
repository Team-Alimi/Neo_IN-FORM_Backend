#!/bin/bash
# 컨테이너 최초 기동 시 1회만 실행됩니다 (데이터 디렉토리가 비어 있을 때).
# 슈퍼유저 권한이 필요한 두 가지를 처리합니다.
#   1) pg_bigm 확장 설치 — trusted extension이 아니라 앱 계정으로는 불가
#   2) inform_crawler 롤 생성 — 롤은 DB가 아니라 클러스터에 속하므로 Flyway 밖
#
# 이후 스키마·인덱스·트리거·GRANT는 전부 Flyway가 담당합니다.
set -e

: "${CRAWLER_PASSWORD:?CRAWLER_PASSWORD 환경변수가 필요합니다}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- 한글 2글자 검색용 2-gram 확장
    CREATE EXTENSION IF NOT EXISTS pg_bigm;

    -- 크롤러 전용 LOGIN 롤.
    -- 실제 테이블 권한은 Flyway V5에서 컬럼 단위로 부여합니다.
    -- "크롤러는 status/카운터를 수정할 수 없다"가 DB 레벨에서 강제됩니다.
    --
    -- ★ IF NOT EXISTS 라 비밀번호는 롤을 "처음 만들 때" 한 번만 설정됩니다.
    --   이 스크립트 자체도 데이터 디렉토리가 빈 경우에만 실행되고, 저장소 어디에도
    --   ALTER ROLE 경로가 없습니다. 그래서 CRAWLER_PASSWORD 를 나중에 고쳐도
    --   살아 있는 롤에는 아무 영향이 없습니다 — .env 의 값은 "현재 비밀번호" 가 아니라
    --   "볼륨 생성 시점의 값" 입니다. 둘은 조용히 어긋납니다.
    --   바꾸려면 ALTER ROLE 을 직접 실행하고 .env·Secret 을 함께 맞춰야 합니다.
    --   (docs/infra/CRAWLER_DB_CONNECTION.md §5 참조)
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'inform_crawler') THEN
            CREATE ROLE inform_crawler LOGIN PASSWORD '${CRAWLER_PASSWORD}';
        END IF;
    END
    \$\$;

    GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO inform_crawler;
    GRANT USAGE   ON SCHEMA public          TO inform_crawler;
EOSQL

echo "[inform] pg_bigm 확장 및 inform_crawler 롤 준비 완료"
