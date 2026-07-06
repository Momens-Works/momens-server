# 0007. Signal backing과 모듈 경계: 신규 `signals` + `signal` 모듈

- 상태: Accepted
- 날짜: 2026-07-06
- 작성자: Kimgyuilli

## 맥락

모바일 MVP는 프로젝트 단위 Signal 목록/상세 조회와 `convert-to-task`, `dismiss` action을 제공해야 한다.
초기 검토에서는 기존 `memory_candidates`를 Signal backing으로 재사용하는 안이 있었지만, 회의 결과
모바일 Signal과 웹 memory candidate는 제품 의미와 lifecycle이 아직 안정적으로 같지 않다고 판단했다.

또한 `mobile` 모듈은 bootstrap/member 조회처럼 여러 도메인의 public API를 조합하는 얇은 orchestration
모듈로 정의되어 있다. Signal은 원본 테이블, evidence 연결, action ledger, 멱등성, outbox 발행까지
소유해야 하므로 단순 모바일 표면이 아니라 별도 도메인 capability에 가깝다.

## 결정

모바일 Signal backing은 신규 `signals` 테이블로 둔다. 기존 `memory_candidates`는 모바일 Signal API의
backing으로 재사용하지 않는다.

서버에는 신규 `signal` Gradle 모듈을 추가한다. 이 모듈은 다음을 소유한다.

- `signals` 조회 모델
- `signal_evidence`를 통한 `source_refs` 근거 연결
- `signal_actions` action ledger와 멱등성
- Signal 목록/상세 및 `convert-to-task`, `dismiss` API
- Signal action 결과 outbox 발행 계약

Signal type은 `decision`, `risk`, `question`, `change`를 사용한다. MVP 목록은 처리되지 않은 Signal만
반환하고, 처리된 Signal inbox는 MVP 이후로 둔다.

## 대안

- `memory_candidates` 재사용: 기존 worker 산출물을 활용할 수 있지만, memory review lifecycle과 Signal
  action lifecycle을 결합하게 된다. type/status 매핑도 별도 정책이 필요하고, 추후 제품 의미가 갈라질 때
  되돌리기 어렵다.
- `mobile` 모듈에 구현: 초기 API 추가는 빠르지만, `mobile` 모듈이 영속성·도메인 정책·outbox를 소유하게
  되어 기존 "얇은 orchestration" 경계와 어긋난다.

## 결과

모바일 Signal 요구사항 변화에 독립적으로 대응할 수 있고, memory candidate와 Signal lifecycle을 섞지 않는다.
대신 신규 테이블과 신규 모듈이 필요하며, worker는 `signals`와 `signal_evidence`를 생성하는 계약을 구현해야
한다.
