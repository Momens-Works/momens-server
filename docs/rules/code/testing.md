# 테스트

테스트 프레임워크는 JUnit 5를 사용합니다. 테스트 유형:

- 애플리케이션 컨텍스트 로드 테스트
- Spring Modulith 경계 테스트
- controller / web 테스트
- service 단위 테스트
- repository 통합 테스트 — PostgreSQL Testcontainers 사용
  ([영속성 > DB](../data/persistence.md#db) 참고, H2 미사용)
