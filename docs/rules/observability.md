# 관측성

WAS 운영 중 요청 흐름을 추적하기 위한 metrics · traces · logs 규칙입니다.

## 적용 범위

관측성 도입 방향과 설정 원칙입니다.

- Spring Boot Actuator는 기본으로 사용합니다.
- Spring Boot 4.1의 OpenTelemetry/Micrometer Tracing 지원을 우선 활용합니다.
- 별도 `logback-spring.xml`이나 직접 만든 MDC filter는 먼저 추가하지 않습니다.
- 지표를 수집·저장할 백엔드는 아직 정하지 않았습니다. OpenTelemetry collector, sampling 비율,
  exporter endpoint, 운영 대시보드, 경보는 그 결정에 딸린 항목입니다.
- 백엔드가 정해지지 않았어도 [지표 규약](#지표-규약)은 지금 지킵니다. 계측 코드는 백엔드에
  독립적이고, 이름과 태그는 나중에 바꾸는 비용이 가장 큰 항목이기 때문입니다.

## 관측성 데이터

| 구분 | 목적 | 예 |
| --- | --- | --- |
| metrics | 시스템과 엔드포인트 상태를 집계 | 요청 수, 응답 시간, JVM 메모리, DB connection pool, 에러율 |
| traces | 하나의 요청 흐름을 추적 | controller → service → DB → 외부 API |
| logs | 개별 이벤트를 기록 | 비즈니스 이벤트, 경고, 예외 |

## 지표 규약

지표는 Micrometer로 계측합니다. 아래는 수집 백엔드가 바뀌어도 그대로 유효한 규칙만 담습니다.

### 이름

```text
momens.<모듈>.<대상>.<측정>
```

- 소문자와 점만 씁니다. Micrometer가 백엔드별 표기로 변환하므로 백엔드 표기를 코드에 직접 쓰지
  않습니다(`http.server.requests` → Prometheus `http_server_requests_duration_seconds`).
- `<모듈>`은 모듈 이름입니다. 단일 배포 단위이므로 서비스 구분이 아니라 도메인 구분입니다.
- `<대상>`은 모듈 내부에서 쓰는 개념 이름을 그대로 씁니다.
- `<측정>`은 재는 대상입니다. counter·summary는 복수 명사, Observation은 동사를 씁니다.
- 어떤 일에 걸린 시간은 언제나 `duration`입니다. `latency`·`elapsed`·`response.time`을 섞어 쓰면
  검색이 깨집니다. 무언가가 그 시점에 얼마나 오래됐는지는 `age`로, 서로 다른 개념이라 구분합니다.
  `age`는 애플리케이션이 아니라 값의 출처와 같은 시계로 계산합니다. 인스턴스 간 시계 편차가 그대로
  지표 편차가 됩니다.
- **단위를 이름에 넣지 않고 `baseUnit`으로 선언합니다.** Prometheus 계열이 요구하는 `_seconds`·
  `_total` 접미사는 registry가 변환 시점에 붙이므로, 코드에 직접 쓰면 이중으로 붙습니다.
- **태그로 나눌 값을 이름에 넣지 않습니다.** `...requests.failed`가 아니라 `...requests{outcome}`입니다.
- 버전·인스턴스·환경·배포·기술 스택은 이름에 넣지 않습니다. 전부 태그나 resource 속성입니다.
  이름에 들어가면 그 값이 바뀔 때 대시보드·경보·과거 데이터가 함께 끊깁니다.

모듈을 태그로 빼지 않고 이름에 두는 이유는 아래 "전체 집계" 규칙 때문입니다. 모듈을 태그로 두면
모듈을 가로지른 집계가 가능해져야 하는데, 구조가 닮은 지표라도 어휘가 겹치지 않으면 그 집계는
의미가 없습니다. `minsu`의 생성 원장과 `notification`의 발송 원장이 lease·claim 구조를 공유하지만
완료 사유 어휘는 전혀 겹치지 않습니다. 이런 경우는 태그가 아니라 지표를 나눕니다.

### 태그

| 키 | 뜻 | 값 |
| --- | --- | --- |
| `outcome` | 한 번의 실행이 어떻게 끝났는가 | 코드 enum의 고정 어휘 |
| `<무엇>.reason` | `outcome`을 세분화한 사유 | 고정 어휘. 접두로 무엇의 사유인지 밝힘 |
| `status` | 지속되는 상태의 구분 | 고정 어휘. 사건이 아니라 상태일 때만 |
| `mode` | 같은 일을 하는 경로의 구분 | 예: `sync` / `async` |

- 키는 이름과 같은 소문자 점 표기, 값은 소문자 snake_case 고정 어휘입니다.
- **전체 집계가 의미를 가져야 합니다.** 모든 태그를 가로지른 `sum()`이나 `avg()` 중 하나가 의미
  없으면 태그가 아니라 지표를 나눕니다. 특히 **한 태그의 값 집합에 다른 값들의 합계를 넣지
  않습니다.** 합산이 이중 계상됩니다.
- **값이 없어도 태그를 빼지 않고 명시적인 값을 씁니다**(`none` 등). 시계열마다 태그 집합이 달라지면
  집계가 깨집니다.
- **태그 조합이 부팅 시점에 모두 정해지는 지표는 `0`으로 미리 등록합니다.** 등록하지 않으면 재시작
  후 첫 사건 전까지 시계열이 없어 `rate()`가 값을 내지 못하고, 그 구간이 장애와 구분되지 않습니다.
  실제 선택된 모델처럼 런타임에 정해지는 태그가 섞여 있으면 예외이며, 그 사실을 지표를 만드는
  자리에 남깁니다.

### 카디널리티

- **금지** — UUID, 이메일, `workspace_id`·`task_id`·`user_id`, 자유 텍스트, 예외 메시지, 경로 원문.
- **허용** — 코드에 열거된 유한 집합, 설정에서 오는 유한 값(`provider`, `model`, `prompt.version`).
- 사용자 입력에서 온 값은 유한 집합으로 정규화한 뒤 태그로 씁니다(예: 404를 URI가 아니라
  `not_found`로).
- 지표 하나의 카디널리티는 10 미만을 목표로 하고, 100을 넘으면 설계를 다시 봅니다.
- 개별 사건을 되짚어야 하면 지표가 아니라 로그와 `traceId`로 갑니다. 이 문서 앞의 metrics·logs
  구분 그대로입니다.

### 계기 선택

| 계기 | 쓰는 곳 | 쓰지 않는 곳 |
| --- | --- | --- |
| counter | 일어난 사건의 누적 | 줄어들 수 있는 값 |
| gauge | 지금 이 순간의 상태 | 누적 총량 |
| timer | 걸린 시간 | |
| summary | 시간이 아닌 분포(토큰 수, 시도 횟수) | |

- 판별 기준은 하나입니다. **값이 내려갈 수 있으면 gauge, 아니면 counter입니다.**
- **counter로 셀 수 있는 것을 gauge로 재지 않습니다.** 누적 총량을 gauge로 두면 증가율을 구할 수
  없습니다. counter는 rate와 increase를 모두 줍니다.
- **gauge 콜백에서 비용이 드는 일을 하지 않습니다.** gauge는 수집 시점에만 관측되고 그 사이 값은
  버려집니다. 콜백은 이미 있는 상태를 읽기만 해야 합니다. 집계가 필요하면 주기적으로 스냅샷을
  갱신하고 gauge는 그 값을 읽습니다. 콜백에서 직접 조회하면 수집 주기와 인스턴스 수만큼 쿼리가
  곱해집니다.
- Observation은 외부 호출과 모듈 경계에 씁니다(trace와 지표를 함께 얻습니다). 내부 상태 지표는
  meter를 직접 씁니다.

### 개명

**지표 이름은 사실상 영구적입니다.** 수집이 붙은 뒤 이름을 바꾸면 대시보드·경보·과거 데이터가 모두
끊깁니다. 개명이 필요하다고 판단되면 수집 백엔드가 붙기 전에 합니다.

### 참고

- [Naming Meters — Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/naming.html)
- [Gauges — Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/gauges.html)
- [Metric and label naming — Prometheus](https://prometheus.io/docs/practices/naming/)
- [Instrumentation — Prometheus](https://prometheus.io/docs/practices/instrumentation/)
- [Metrics semantic conventions — OpenTelemetry](https://opentelemetry.io/docs/specs/semconv/general/metrics/)
- [How to Name Your Metrics — OpenTelemetry Blog](https://opentelemetry.io/blog/2025/how-to-name-your-metrics/)

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

로그 상관관계를 위해 `spring-boot-starter-opentelemetry`를 포함합니다(OTel SDK 자동구성 +
Micrometer Tracing 브리지를 함께 제공 — 브리지 단독 의존성으로는 `Tracer` 빈이 배선되지
않습니다). 그러면 Spring Boot가 `traceId`와 `spanId` 기반 correlation id를 자동으로 로그에
채웁니다. 별도 request id를 직접 생성하기보다 내부 요청 추적에는 `traceId`를 우선 사용합니다.

starter가 OTLP exporter도 함께 들이므로, collector가 없는 동안 기본 endpoint로 전송을 시도해
연결 오류가 납니다. 따라서 trace context(로그 상관관계)는 유지하되 export는 기본 비활성화합니다
(`management.tracing.export.enabled: false`, `management.otlp.metrics.export.enabled: false`,
`management.logging.export.otlp.enabled: false`). OTLP collector endpoint와 sampling 비율은 수집
백엔드를 정하고 배선할 때 켭니다(위 [적용 범위](#적용-범위) 참고).

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
