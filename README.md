<img src="docs/assets/logo.png" alt="Momens" width="112" align="left" />

# MOMENS

<img alt="모먼스" src="https://img.shields.io/badge/%EB%AA%A8%EB%A8%BC%EC%8A%A4-4A4A4A?style=for-the-badge" /> <img alt="Turn context into momentum" src="https://img.shields.io/badge/Turn%20context%20into%20momentum-4570EF?style=for-the-badge" />

<br clear="left" />

<img src="docs/assets/readme-banner.webp" alt="Momens" width="100%" />

## Turn context into momentum, Momens

> 흩어진 업무 맥락을 연결해 프로젝트의 리스크, 결정 사항과 다음 행동을 놓치지 않도록
> 돕는 AI 운영 도구입니다.

Momens의 새로운 Java/Spring Product API 서버입니다.

기존 Go/Gin Product API 서버인 `momens-api`를 대체하기 위해 점진적으로 기능을 이관합니다.

## Product Deck

<details open>
<summary>제품 상세 소개 접기</summary>

<img src="docs/assets/product-deck.webp" alt="Momens Product Deck" width="100%" />

</details>

## Architecture

`momens-server`는 **modular modulith**를 지향합니다.

- 런타임은 하나의 Spring Boot 애플리케이션으로 구성합니다.
- 물리적인 모듈 경계는 Gradle 서브프로젝트이며, `app` 모듈에서 각각의 모듈을 조립하여
  애플리케이션을 실행합니다.
- Spring Modulith를 함께 사용하여 모듈 간 의존 관계와 경계를 검증하고 문서화합니다.
- 새로운 요구사항이 추가될 경우 그에 맞춰 모듈 구조를 단계적으로 확장합니다.
- 팀 규모가 커지고, 배포의 독립성을 필수로 확보해야 할 시점에 MSA 전환을 고려합니다.

## Docs

사람이 읽는 `docs/` 문서는 한국어로 작성되며, AI 에이전트가 읽는 문서는
[AGENTS.md](AGENTS.md)로 관리합니다.

- [문서 인덱스](docs/README.md)
- [온보딩](docs/onboarding.md)
- [로컬 개발](docs/local-development.md)
- [공통 규칙](docs/rules/README.md)
- [서버 명세](docs/spec/README.md)
- [상세 설계](docs/design/)
- [아키텍처 결정 기록](docs/adr/README.md)

## Tech Stack

| Area | Choice |
| --- | --- |
| Language | <img alt="Java 21" src="https://img.shields.io/badge/Java%2021-437291?style=for-the-badge&logo=openjdk&logoColor=white" /> |
| Framework | <img alt="Spring Boot 4" src="https://img.shields.io/badge/Spring%20Boot%204-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" /> |
| Architecture support | <img alt="Spring Modulith" src="https://img.shields.io/badge/Spring%20Modulith-6DB33F?style=for-the-badge&logo=spring&logoColor=white" /> |
| Database | <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" /> |
| Persistence | <img alt="JPA" src="https://img.shields.io/badge/JPA-59666C?style=for-the-badge&logo=hibernate&logoColor=white" /> |
| Migration | <img alt="Flyway" src="https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white" /> |
| Test | <img alt="JUnit 5" src="https://img.shields.io/badge/JUnit%205-25A162?style=for-the-badge&logo=junit5&logoColor=white" /> <img alt="Testcontainers" src="https://img.shields.io/badge/Testcontainers-291A3E?style=for-the-badge" /> |
| Build | <img alt="Gradle" src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white" /> <img alt="Groovy DSL" src="https://img.shields.io/badge/Groovy%20DSL-4298B8?style=for-the-badge&logo=apachegroovy&logoColor=white" /> |
| Formatting | <img alt="Spotless" src="https://img.shields.io/badge/Spotless-4A4A4A?style=for-the-badge" /> <img alt="Google Java Format" src="https://img.shields.io/badge/Google%20Java%20Format-4285F4?style=for-the-badge&logo=google&logoColor=white" /> |

## Maintainers

| 김규일 \| Lead | 신진수 \| Member |
| :---: | :---: |
| <img src="docs/assets/card-gyuil.png" alt="김규일" width="240" /> | <img src="docs/assets/card-jinsu.png" alt="신진수" width="240" /> |
| [@Kimgyuilli](https://github.com/Kimgyuilli) | [@jsshin8128](https://github.com/jsshin8128) |
