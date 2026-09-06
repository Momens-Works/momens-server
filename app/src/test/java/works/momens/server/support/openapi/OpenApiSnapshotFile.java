package works.momens.server.support.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

/**
 * 커밋된 OpenAPI 스냅샷 파일의 경로를 제공합니다. {@link OpenApiSnapshotTest}와 {@link OpenApiOperationIdTest}가 함께
 * 사용합니다. Postgres 컨테이너를 기동하는 테스트 클래스와 경로 조회 로직을 분리해, 스냅샷 경로만 읽는 테스트에서는 컨테이너가 기동되지 않도록 합니다.
 */
final class OpenApiSnapshotFile {

  /** 스냅샷 파일 경로. Gradle이 rootProject 기준 절대경로로 주입해 실행 위치에 의존하지 않는다. */
  private static final String PATH_PROPERTY = "momens.openapi.snapshot.path";

  private OpenApiSnapshotFile() {}

  static Path path() {
    String configured = System.getProperty(PATH_PROPERTY);
    assertThat(configured)
        .as("%s 시스템 프로퍼티가 필요합니다. Gradle test task 설정을 확인하세요.", PATH_PROPERTY)
        .isNotBlank();
    return Path.of(configured);
  }
}
