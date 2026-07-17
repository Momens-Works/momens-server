package works.momens.server.minsu.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 민수 Vertex AI 설정. 활성화({@code momens.minsu.enabled=true})된 환경에서만 바인딩됩니다.
 *
 * <p>{@code project}·{@code location}은 Vertex 백엔드 필수값이라 활성 시 비어 있으면 부팅을 막습니다. env 이름은 레거시
 * momens-api와 동일하게 {@code GOOGLE_CLOUD_PROJECT}·{@code GOOGLE_CLOUD_LOCATION}을 씁니다(배포 설정 재사용).
 * {@code model}은 미지정 시 {@code gemini-2.5-flash}를 씁니다(momens-api 기본값과 동일).
 */
@Validated
@ConfigurationProperties(prefix = "momens.minsu")
record MinsuProperties(@NotBlank String project, @NotBlank String location, String model) {

  MinsuProperties {
    if (model == null || model.isBlank()) {
      model = "gemini-2.5-flash";
    }
  }
}
