# Java 21 런타임 환경 사용
FROM eclipse-temurin:21-jre-jammy

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 jar 파일을 컨테이너 내부로 복사
# GitHub Actions 빌드 결과물 위치에 맞춤
COPY *-SNAPSHOT.jar app.jar

# 서버 포트 개방
EXPOSE 8080

# 어플리케이션 실행 (메모리 최적화 옵션 포함)
ENTRYPOINT ["java", "-Xmx512m", "-Dserver.port=8080", "-jar", "app.jar"]
