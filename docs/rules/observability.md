# 관측성

WAS 운영 중 요청 흐름을 추적하기 위한 metrics · traces · logs 규칙입니다.

## 초기 범위

초기 세팅 단계에서는 관측성 도입 방향과 설정 원칙만 확정합니다.

- Spring Boot Actuator는 기본으로 사용합니다.
- Spring Boot 4.1의 OpenTelemetry/Micrometer Tracing 지원을 우선 활용합니다.
- 별도 `logback-spring.xml`이나 직접 만든 MDC filter는 먼저 추가하지 않습니다.
- OpenTelemetry collector, sampling 비율, exporter endpoint, 운영 대시보드는 배포 환경이 정해질 때
  구체화합니다.

## 관측성 데이터

| 구분 | 목적 | 예 |
| --- | --- | --- |
| metrics | 시스템과 엔드포인트 상태를 집계 | 요청 수, 응답 시간, JVM 메모리, DB connection pool, 에러율 |
| traces | 하나의 요청 흐름을 추적 | controller → service → DB → 외부 API |
| logs | 개별 이벤트를 기록 | 비즈니스 이벤트, 경고, 예외 |

## OpenTelemetry

Spring Boot 4.1은 OpenTelemetry OTLP tracing을 위한
`org.springframework.boot:spring-boot-starter-opentelemetry` starter를 제공합니다.

관측성 구현 시점에는 아래를 기준으로 둡니다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'
```

주요 설정 축:

- `management.opentelemetry.enabled`
- `management.opentelemetry.tracing.sampler`
- `management.tracing.sampling.probability`
- `management.opentelemetry.tracing.export.otlp.*`
- `management.opentelemetry.tracing.limits.*`
- `management.opentelemetry.logging.export.otlp.*`
- `management.opentelemetry.logging.limits.*`

초기 검증 목표는 다음입니다.

> HTTP 요청 하나가 WAS 내부 처리와 외부 API 호출까지 하나의 trace로 이어져 보이는가.

자동 trace 전파가 필요하면 Spring Boot가 auto-configure한 `RestClient.Builder`,
`WebClient.Builder`, `RestTemplateBuilder`를 사용합니다. 직접 생성한 HTTP client는 trace context
전파가 누락될 수 있습니다.

## 로그 상관관계

Spring Boot의 기본 로깅은 Logback입니다. OpenTelemetry starter가 Logback을 대체하지 않습니다.

Micrometer Tracing이 활성화되면 Spring Boot는 기본적으로 `traceId`와 `spanId` 기반 correlation
id를 로그에 포함할 수 있습니다. 별도 request id를 직접 생성하기보다 내부 요청 추적에는
`traceId`를 우선 사용합니다.

| 목적 | 식별자 |
| --- | --- |
| 로그 상관관계 | `traceId`, `spanId` |
| 서비스 간 분산 추적 | OpenTelemetry trace context |
| 외부 요청 ID 보존 | `requestId` |

외부에서 `X-Request-Id`가 들어오거나 gateway, partner API, 감사 로그 요구가 있을 때만
`requestId`를 별도 보존합니다.

로그 correlation pattern을 직접 지정해야 하면 Spring Boot의 `logging.pattern.correlation`
설정을 사용합니다.

```yaml
logging:
  pattern:
    correlation: "[%X{traceId:-},%X{spanId:-}] "
```

직접 MDC를 다룰 때는 `MDC.clear()`로 전체를 지우지 않습니다. 내가 넣은 key만 제거해
trace 관련 key를 지우지 않도록 합니다.

## 로그 출력 포맷

로컬과 테스트에서는 사람이 읽기 쉬운 console 로그를 유지합니다.

운영에서 JSON 로그가 필요하면 직접 JSON appender를 만들기보다 Spring Boot 4.1의 structured
logging 설정을 우선 검토합니다.

```yaml
logging:
  structured:
    format:
      console: logstash
```

로그 호출부는 출력 포맷에 의존하지 않게 작성합니다.

- 문자열 연결 대신 `{}` placeholder를 사용합니다.
- 주요 도메인 식별자를 명시적으로 남깁니다.
- 민감 정보는 남기지 않습니다.
- 구조적 로그 key가 필요하면 SLF4J fluent logging 또는 Spring Boot structured logging 설정을
  검토합니다.
