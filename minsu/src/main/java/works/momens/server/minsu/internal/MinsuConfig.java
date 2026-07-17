package works.momens.server.minsu.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.momens.server.minsu.Minsu;

/**
 * 민수 Vertex AI(Gemini) 배선.
 *
 * <p>{@code momens.minsu.enabled=true}일 때만 로드합니다. 인증 정보는 Application Default Credentials로 읽습니다 —
 * dev 배포는 GKE Workload Identity로 전용 서비스 계정을 연결하며 JSON 키나 credential Secret을 쓰지 않습니다(notification
 * FCM과 같은 방식). 비활성 환경은 {@link DisabledMinsuConfig}가 실패 응답 빈을 대신 등록합니다.
 *
 * <p>{@link Client}는 {@link AutoCloseable}이라 Spring이 종료 시 닫습니다. 빌드 시점에는 자격증명을 확인하지 않고 첫 호출에서 ADC를
 * 해석하므로, 활성이지만 자격증명이 없는 환경도 부팅은 되고 첫 생성 호출에서 실패합니다.
 */
@Configuration
@ConditionalOnProperty(name = "momens.minsu.enabled", havingValue = "true")
@EnableConfigurationProperties(MinsuProperties.class)
class MinsuConfig {

  @Bean
  Client minsuGenaiClient(MinsuProperties properties) {
    return Client.builder()
        .vertexAI(true)
        .project(properties.project())
        .location(properties.location())
        .build();
  }

  @Bean
  Minsu minsu(Client minsuGenaiClient, MinsuProperties properties, ObjectMapper objectMapper) {
    return new GeminiMinsu(minsuGenaiClient, properties.model(), objectMapper);
  }
}
