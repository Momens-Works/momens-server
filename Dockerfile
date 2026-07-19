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

# 1) 의존성 워밍 레이어: 빌드 스크립트·wrapper·proto 서브모듈만 먼저 복사해 Gradle 배포본과
#    의존성 메타데이터를 미리 받아 둡니다. 소스만 바뀌면 아래 COPY . . 이후 레이어만
#    무효화되고 이 레이어는 캐시 히트되어, 매 빌드마다 Gradle 배포본/메타데이터를 다시
#    받지 않습니다. 새 모듈을 추가하면 해당 모듈의 build.gradle COPY 행도 여기에 추가합니다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY third_party ./third_party
COPY app/build.gradle ./app/
COPY auth/build.gradle ./auth/
COPY common/build.gradle ./common/
COPY context/build.gradle ./context/
COPY mobile/build.gradle ./mobile/
COPY notification/build.gradle ./notification/
COPY outbox/build.gradle ./outbox/
COPY project/build.gradle ./project/
COPY signal/build.gradle ./signal/
COPY source/build.gradle ./source/
COPY user/build.gradle ./user/
COPY workspace/build.gradle ./workspace/
RUN sed -i 's/\r$//' gradlew
RUN ./gradlew --no-daemon \
      -Dorg.gradle.java.installations.paths=/opt/java/jdk17 \
      :app:dependencies --configuration runtimeClasspath -q || true

# 2) 소스 복사 후 빌드. 테스트는 PostgreSQL Testcontainers(Docker)가 필요해 이미지 빌드
#    중 실행할 수 없습니다. CI 가 spotlessCheck/test 를 별도로 수행하므로 여기서는 bootJar
#    만 만듭니다.
COPY . .
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
