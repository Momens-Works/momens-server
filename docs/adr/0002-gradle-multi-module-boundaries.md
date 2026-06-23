# 0002. Gradle 멀티모듈을 물리 모듈 경계로 사용

- 상태: Accepted
- 날짜: 2026-06-24
- 작성자: Kimgyuilli

## 맥락

[ADR-0001](0001-modular-monolith-rules.md)에서 모듈러 모놀리스와 Spring Modulith 적용을
확정했지만, 거기서 말하는 "모듈"이 **Gradle 서브프로젝트**인지 **Spring Modulith
application module(패키지)** 인지 명시하지 않았다. 그 결과 첫 작업(DB/JPA 베이스 패턴) 착수
중에 "단일 Gradle 모듈 안의 패키지 기반 모듈러 모놀리스"와 "Gradle 멀티모듈 기반 모듈러
모놀리스" 두 해석이 충돌했다.

전제로 둘은 별개의 축이다.

- Spring Modulith는 **패키지** 기준으로 경계를 검증한다(테스트/런타임). Gradle 구성을 알지
  못한다.
- Gradle 멀티모듈은 **서브프로젝트** 기준으로 컴파일 의존을 격리한다(빌드 타임).

이 ADR은 둘 중 무엇을 **물리 모듈 경계**로 삼을지 확정한다. ADR-0001을 대체하지 않고,
거기서 비워 둔 "모듈의 물리적 정의"를 보완한다.

## 결정

`momens-server`는 **Gradle 멀티모듈 기반 모듈러 모놀리스**로 설계한다.

1. **물리 모듈 경계 = Gradle 서브프로젝트**. 기능/도메인 단위로 나눈다.
   `controller`/`service`/`repository` 같은 **레이어 단위로 Gradle 모듈을 나누지 않는다.**
2. `app` 모듈이 각 기능 모듈을 **조립하고 실행**하는 유일한 실행 가능 모듈이다.
3. 기능 모듈끼리는 **직접 참조를 최소화**한다.
4. 모듈 간 통신은 가능하면 **Spring application event 또는 공개 API**를 통해 한다.
5. `common`/global 모듈은 **최소화**한다.
6. **Spring Modulith는 포기하지 않고 병행**한다. 단일 모듈 전용이 아니며, 멀티모듈에서도
   의존성 검증·문서화·이벤트 기반 통신·모듈 테스트 용도로 사용한다.

기능 모듈 내부에서는 필요할 때만 `presentation`/`application`/`domain`/`infrastructure`
레이어를 둔다(ADR-0001 결정 2).

예시 구조:

```text
root
├── app            # 실행·조립 (Spring Boot main)
├── user           # 기능/도메인 모듈
├── post
├── comment
├── notification
├── auth
└── common         # 최소화된 공유 모듈
```

모든 모듈은 같은 base package `works.momens.server.*` 아래의 패키지를 사용해, `app`
클래스패스에서 Spring Modulith가 하나로 조립해 경계를 검증할 수 있게 한다.

## 대안

- **단일 Gradle 모듈 + 패키지 기반 모듈**(Gradle은 `app` 하나, capability는 패키지):
  Spring Modulith만 쓰는 가장 가벼운 구성이지만, 컴파일 레벨 격리가 없어 모듈 간 우발적
  결합을 빌드가 막아 주지 못한다. 물리 경계를 명시적으로 두려는 의도와 맞지 않아 기각.
- **레이어별 Gradle 멀티모듈**(`web`/`service`/`domain` 등으로 분리): 기술 계층이 모듈
  경계가 되어 ADR-0001 결정 1과 정면으로 충돌하므로 기각.

## 결과

- 모듈 의존이 각 `build.gradle`에 명시되어 빌드 타임에 강제되고, Spring Modulith가
  런타임 패키지 경계까지 이중으로 검증한다.
- 공유 코드(예: 영속성 베이스)는 `common` 같은 별도 모듈에 둬야 한다. `app`에 두면
  기능 모듈이 `app`에 의존해 `app → 기능 모듈`과 순환된다.
- 초기 ceremony(서브프로젝트별 `build.gradle`, BOM/플러그인 적용)가 늘어난다. 기능
  모듈은 라이브러리(`java-library`)로, `app`만 실행 jar로 둔다.
- 통합 테스트처럼 전체 Spring 컨텍스트가 필요한 검증은 조립 지점인 `app`에서 수행한다.
