# 컨벤션

`momens-server`의 초기 컨벤션입니다.
세부 규칙은 세팅과 구현을 진행하면서 필요한 만큼 추가합니다.

## 패키지

기본 패키지:

```text
works.momens.server
```

실제 Gradle 모듈 목록은 추후 결정합니다.
아래는 논의용 예시입니다.

```text
works.momens.server.platform
works.momens.server.workspace
works.momens.server.product
works.momens.server.memory
works.momens.server.source
works.momens.server.retrievalintegration
```

검색 연동 모듈은 별도 서비스 `momens-retrieval`과 구분하기 위해
`retrieval-integration`을 우선 후보로 둡니다.

## API Compatibility

초기 세팅 단계에서는 새 공통 API 응답 포맷을 정하지 않습니다.
우선 기존 Go API와의 호환을 원칙으로 두고, 응답 포맷 정리는 마이그레이션
단계에서 결정합니다.

