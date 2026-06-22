# Spring

## 의존성 주입

- 생성자 주입만 사용합니다(필드 주입 금지). `@RequiredArgsConstructor` + `private final`을 권장합니다.

## 레이어 책임

- Controller: HTTP 입출력에 집중하고 얇게 유지합니다.
- Service: 오케스트레이션·교차 엔티티 규칙·트랜잭션 경계.
- Entity: 자기 상태·불변식 로직(자신을 어떻게 바꿀지)을 담습니다.
- Repository: DB 접근을 캡슐화합니다(그 뒤로 숨김). 초기 접근은 JPA.
- 공통 인프라 코드는 platform 성격의 위치에 둡니다.

## 검증

- 요청 DTO에는 validation annotation을 사용합니다.

## 트랜잭션

- 트랜잭션 경계는 Service 메서드에 둡니다.

## 보안

- Spring Security 의존성은 초기부터 포함하되, 보안 설정 클래스는 인증/인가 구현 시점에 만듭니다.
