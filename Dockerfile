# syntax=docker/dockerfile:1

# momens-proto 서브모듈(:app→:signal→:momens-proto)은 Java 17 toolchain 을 요구합니다.
# CI 러너에는 JDK 17 이 기본 설치돼 Gradle 이 자동탐지하지만, 아래 빌드 이미지에는
# JDK 21 만 있어 여기서 JDK 17 을 별도로 제공합니다.
FROM eclipse-temurin:17-jdk-jammy AS jdk17

# 빌드 스테이지: Temurin 21 JDK (CI 의 distribution/version 과 일치).
FROM eclipse-temurin:21-jdk-jammy AS build
# momens-proto(Java 17) 컴파일용 toolchain. installations.paths 로 Gradle 에 이 경로를 알려줍니다.
COPY --from=jdk17 /opt/java/openjdk /opt/java/jdk17
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew
# 테스트는 PostgreSQL Testcontainers(Docker)가 필요해 이미지 빌드 중 실행할 수 없습니다.
# CI 가 spotlessCheck/test 를 별도로 수행하므로 여기서는 bootJar 만 만듭니다.
RUN ./gradlew --no-daemon \
      -Dorg.gradle.java.installations.paths=/opt/java/jdk17 \
      :app:bootJar -x test \
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
