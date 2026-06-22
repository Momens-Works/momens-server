# 로깅

- 로거는 Lombok `@Slf4j`를 사용합니다.
- 로그 인자는 문자열 연결 대신 `{}` placeholder로 전달합니다
  (`log.info("user {} created", userId)`).
- 민감 정보(비밀번호·토큰·개인정보 등)는 로그에 남기지 않습니다([시크릿](../config/secrets.md) 참고).
- 로그 레벨:
  - `error`: 시스템 오류·예외
  - `warn`: 경고·복구 가능한 문제
  - `info`: 주요 비즈니스 흐름
  - `debug`: 개발 디버깅
- 로그 출력 포맷(plain vs 구조적/JSON)은 관측 스택과 함께 정합니다([P13](../../DECISIONS-PENDING.md)).
