package works.momens.server.support.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ApiVersionConfig implements WebMvcConfigurer {

  static final String API_VERSION_HEADER = "API-Version";
  static final String V1 = "1";

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    // 레거시 호환 API는 아직 version header를 강제하지 않고, version mapping이 있는 신규 API만
    // API-Version 헤더를 기준으로 분기합니다.
    configurer
        .useRequestHeader(API_VERSION_HEADER)
        .addSupportedVersions(V1)
        .setVersionRequired(false);
  }
}
