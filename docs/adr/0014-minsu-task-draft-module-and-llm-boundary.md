# 0014. Minsu task draft 모듈과 LLM 경계

- 상태: Accepted
- 날짜: 2026-07-25
- 작성자: Kimgyuilli

## 맥락

ADR-0011은 Signal을 태스크로 등록할 때 민수가 `title`, `role`, `priority`로 구성된 task
draft를 생성하고, 민수 구현 전에는 api-server가 고정 draft를 사용하도록 정했다. 현재
`SignalActionServiceImpl`은 Signal title과 `pm`, `medium`을 직접 조합한다.

레거시 `momens-api`에는 이미 Minsu query, Slack bot, action routing과 Google Gen AI client가
있다. 레거시의 공유 LLM client는 Application Default Credentials(ADC), 응답 candidate·finish
reason 검증, token·response ID 관측, 비정상 응답 fallback에 참고할 수 있다. 다만 레거시 Minsu의
`create_task` action은 대화에서 프로젝트와 태스크 입력을 추출해 바로 태스크를 만드는 기능이다.
이미 프로젝트가 정해진 Signal을 body 없는 모바일 action으로 수용하는 이번 경로와는 유스케이스와
API 계약이 다르다.

모바일의 최초 연동만을 위해 Google SDK를 `signal`에 직접 넣으면 향후 웹 Minsu query와 prompt,
model 선택, 관측 기반을 공유하기 어렵다. 반대로 레거시 Minsu query·retrieval·Slack 전체를 함께
이관하면 첫 수직 슬라이스가 지나치게 커진다. 따라서 최소 기능 경계와 LLM 벤더 경계를 함께
결정해야 한다.

## 결정

### 모듈과 공개 계약

`minsu`를 별도 Gradle 기능 모듈로 만들고, 첫 공개 유스케이스는
`SignalTaskDraftGenerator`로 한정한다. `signal`은 `minsu` root package의 공개 계약만 직접
참조한다.

- 입력은 Signal title, type, description, overall impact와 evidence의 target·change·impact다.
- 출력은 title, role, priority가 모두 검증된 task draft다.
- Google SDK 타입, provider 응답, prompt 표현은 공개 계약 밖에 둔다.
- Minsu suggestion, query, retrieval, Slack과 레거시 대화형 `create_task` action은 이번
  유스케이스에 포함하지 않는다.

Minsu query를 이관할 때는 별도 공개 유스케이스를 추가한다. task draft와 query는 `minsu`
모듈의 LLM adapter·model 선택·persona 기반만 공유하고 서로의 API DTO를 재사용하지 않는다.

### LLM port와 Google adapter

`minsu` 내부에 벤더 중립 `LlmClient` port를 두고 최초 구현은 Google Gen AI Java SDK
adapter 하나만 둔다. SDK 타입은 infrastructure adapter 밖으로 노출하지 않는다.

- Google Gen AI Java SDK를 직접 사용한다.
- 구현 시점의 최신 안정 SDK 버전을 확인해 명시적으로 고정한다.
- 안정 API인 `v1`과 ADC를 사용한다.
- Google SDK client는 Spring bean 생성 중 만들지 않고 최초 provider 호출 경계에서 지연
  생성한다. 성공한 client만 재사용하며 ADC 조회나 client 생성 실패는 provider 호출 실패와 같은
  고정 fallback으로 처리한다.
- 기본 및 최초 허용 모델은 GA인 `gemini-3.5-flash-lite`다.
- model은 배포 설정에서 선택하되, 허용 catalog 밖의 provider/model을 조용히 기본값으로
  바꾸지 않는다.
- 호출은 한 번만 수행한다. 애플리케이션 재시도와 SDK 자동 재시도는 두지 않는다.
- 초기 개발 측정에는 별도 애플리케이션 timeout을 두지 않는다. 운영 timeout은 실제 latency
  측정 후 별도 결정한다.

배포 설정을 읽는 내부 `ModelSelectionPolicy`가 provider와 model을 선택한다. 향후 사용자 또는
workspace가 모델을 고르는 요구가 생기면 이 policy와 별도 model catalog를 확장한다. 현재 HTTP
요청, DB, UI에는 provider/model 선택을 노출하거나 저장하지 않는다.

### 결과 검증과 fallback

Google adapter는 `application/json` response MIME type과 structured output schema를 사용한다.
schema는 title, role, priority를 필수로 두고 role과 priority를 enum으로 제한한다.
Signal과 evidence의 문자열은 외부 유래 데이터로 취급해 system instruction과 구조적으로 분리하고,
그 안의 지시문은 따르지 않는다.

태스크 title의 공백 포함 15자 제한은 모델에만 맡기지 않는다.

1. prompt와 title schema description에 15자 이내 생성을 지시한다.
2. 서버가 trim한 뒤 15자 이내로 자른다.
3. 모델 title이 비어 있으면 Signal title을 사용한 뒤 같은 규칙으로 자른다.
4. 고정 fallback의 Signal title에도 같은 규칙을 적용한다.

Google structured output이 지원하지 않는 `maxLength`는 schema에 넣지 않는다. role이
`pm`, `design`, `backend`, `frontend` 밖이거나 priority가 `low`, `medium`, `high` 밖이면
일부 값만 섞지 않고 고정 fallback 전체를 사용한다.

다음 경우에는 외부 실패를 public API 오류로 바꾸지 않고
`Signal title(15자 이내) / pm / medium` 고정 draft를 반환한다.

- 기능 비활성화
- provider/model/location 등 설정 무효
- description, overall impact와 모든 evidence 의미 값이 없음
- ADC 조회 또는 Google SDK client 생성 실패
- rate limit·quota 초과를 포함한 provider 호출 예외
- candidate 없음, 비정상 finish reason, 빈 응답, JSON 또는 enum 검증 실패
- 추후 운영 timeout 초과

잘못된 설정이나 응답은 fallback으로 사용자 흐름을 유지하더라도 로그와 metric에서 원인을
구분한다. prompt, 원문 응답, source 원문·URL·작성자·provider metadata는 로그에 남기지 않는다.
Google provider 호출은 Micrometer `Observation`으로 감싸 현재 HTTP trace의 child span과 호출
시간을 함께 기록한다.

### 호출·트랜잭션 경계

`convert-to-task`는 현재 HTTP 계약과 멱등 정책을 유지한다.

1. Signal 존재와 사용자 권한을 검사한다.
2. 기존 `signal_actions`를 확인하고 replay 또는 충돌이면 모델을 호출하지 않는다.
3. 새 convert 요청일 때만 쓰기 트랜잭션 밖에서 draft를 생성한다.
4. 기존 `SignalActionExecutor` 트랜잭션에서 task, action ledger, outbox를 원자로 저장한다.

동시 최초 요청은 둘 다 모델을 호출할 수 있다. 기존 `UNIQUE(signal_id)`와 race 처리로 최종
task는 한 건만 남지만 중복 호출 비용은 발생할 수 있다. 첫 슬라이스에서는 이를 허용하고 실제
비용이 문제가 될 때 별도 단일 비행 또는 reservation 설계를 검토한다.

task draft는 저장하지 않으므로 DB와 Flyway 변경은 없다.

## 대안

- **Google SDK를 `signal`에 직접 둔다.** 변경량은 가장 작지만 Minsu query와 LLM 설정·관측·prompt
  기반을 공유할 수 없고 `signal`이 외부 벤더를 알게 된다.
- **Spring AI를 사용한다.** 여러 provider adapter와 Spring 관용을 빠르게 얻지만 현재 한 개의
  짧은 structured output 호출에는 추상화와 의존 범위가 크다. 벤더 중립 port를 우리가 소유하면
  필요해질 때 내부 구현만 Spring AI로 교체할 수 있다.
- **Google REST API를 직접 호출한다.** wire contract 통제는 쉽지만 인증, 오류·응답 모델,
  SDK 변화 대응을 직접 소유해야 한다. 공식 Java SDK가 필요한 기능을 제공하므로 택하지 않는다.
- **레거시 Minsu 전체를 먼저 이관한다.** 장기 통합에는 도움이 되지만 query·retrieval·Slack과
  모바일 task draft를 한 PR에 묶어 검증 범위를 키운다.
- **모델 실패를 502/503으로 반환한다.** 잘못된 설정을 즉시 드러내지만 원탭 convert의 핵심
  사용자 흐름을 외부 LLM 가용성에 결합한다. 운영 가시성은 로그·metric으로 확보하고 public
  계약은 유지한다.
- **provider 간 자동 failover를 둔다.** 가용성은 높아지지만 결과 품질·비용·데이터 처리 경계가
  바뀐다. 최초 Google adapter 단계에서는 고정 draft만 fallback으로 사용한다.
- **task draft를 미리 생성해 저장한다.** convert latency는 줄지만 Signal 생성 시점과 사용자
  확정 시점 사이에 draft가 낡을 수 있고 schema 변경도 필요하다. ADR-0011의 convert 시점 생성
  결정을 유지한다.

## 결과

`signal`은 Minsu의 유스케이스 계약에만 의존하고 Google SDK 교체는 `minsu` 내부에 닫힌다.
모바일과 향후 웹은 배포 설정·adapter·관측 기반을 공유하되 각자의 공개 유스케이스 계약은
분리할 수 있다.

LLM 장애나 잘못된 출력이 convert API 가용성과 task 불변식을 깨지 않는다. 그 대신 성공 응답만
보면 모델 문제를 알 수 없으므로 fallback reason과 설정 유효성 관측이 필수다.

초기에는 외부 호출 latency가 그대로 사용자 요청 시간에 더해지고 명시적 timeout이 없다.
MOM-0806에서 dev latency를 측정해 운영 timeout을 확정하기 전에는 prod에서 task draft LLM을
활성화하지 않는다.
