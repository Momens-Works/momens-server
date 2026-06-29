# syntax=docker/dockerfile:1

# 빌드 스테이지: Temurin 21 JDK (CI 의 distribution/version 과 일치).
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew
# 테스트는 PostgreSQL Testcontainers(Docker)가 필요해 이미지 빌드 중 실행할 수 없습니다.
# CI 가 spotlessCheck/test 를 별도로 수행하므로 여기서는 bootJar 만 만듭니다.
RUN ./gradlew --no-daemon :app:bootJar -x test \
 && cp app/build/libs/*.jar /workspace/app.jar

# 런타임 스테이지: Temurin 21 JRE, 일반 사용자 권한으로 실행.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN useradd -r -u 1000 momens
COPY --from=build /workspace/app.jar /app/app.jar
USER momens
EXPOSE 8080
# exec form 으로 PID 1 에 SIGTERM 이 전달되도록 해 graceful shutdown 을 보존합니다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
