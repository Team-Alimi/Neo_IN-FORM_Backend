# 1. Base Image (Java 21)
FROM amazoncorretto:21

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 빌드된 JAR 파일 변수 설정
ARG JAR_FILE=build/libs/*.jar

# 4. JAR 파일을 컨테이너 내부로 복사
COPY ${JAR_FILE} app.jar

# 5. 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]