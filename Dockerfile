# INFORM v2 앱 이미지
#
# ★ 멀티스테이지입니다. 빌드 도구(JDK·Gradle·소스)가 최종 이미지에 남으면 400MB 를 넘고,
#   운영 서버에 소스를 통째로 올리게 됩니다. 실행에 필요한 건 JAR 하나뿐입니다.
#
# ★ 이 이미지는 서버에서 빌드하지 않습니다.
#   운영 인스턴스가 t3.small(2GB) 이라 Gradle 빌드가 OOM 으로 죽습니다.
#   로컬이나 GitHub Actions 에서 빌드해 레지스트리로 올리고, 서버는 pull 만 합니다.
#
#   로컬 빌드:  docker build -t inform-app:local .
#   실행 확인:  docker run --rm -p 8080:8080 --env-file .env inform-app:local

# ── 1단계. 빌드 ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# ★ 의존성 정의만 먼저 복사합니다.
#   소스와 함께 복사하면 코드 한 줄만 고쳐도 의존성을 통째로 다시 받습니다.
#   이 순서면 build.gradle 이 그대로인 한 캐시가 살아 있습니다.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src

# --no-daemon: 컨테이너는 한 번 쓰고 버리므로 데몬이 이득 없이 메모리만 먹습니다.
# -x test:    테스트는 CI 가 이미 돌립니다. 여기서 또 돌리면 DB 컨테이너까지 필요해집니다.
RUN ./gradlew bootJar --no-daemon -x test

# ── 2단계. 실행 ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

# ★ root 로 돌리지 않습니다. 컨테이너가 뚫려도 host 로 번지기 어렵게 합니다.
RUN groupadd -r inform && useradd -r -g inform inform

WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar
RUN chown inform:inform app.jar

USER inform
EXPOSE 8080

# ★ 컨테이너 메모리에 맞춰 힙을 잡게 합니다.
#   이 옵션이 없으면 JVM 이 호스트 전체 메모리를 기준으로 힙을 잡고,
#   컨테이너 한도를 넘겨 OOM Killer 에게 죽습니다 — 로그도 안 남기고 사라집니다.
#
#   UseSerialGC: 2 vCPU 환경에서는 G1 의 병렬 GC 스레드가 오히려 손해입니다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["java", "-jar", "app.jar"]
