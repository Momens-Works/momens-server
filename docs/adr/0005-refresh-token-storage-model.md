# 0005. Refresh token 저장 모델: 서버 저장형 + PostgreSQL 원장

- 상태: Accepted
- 날짜: 2026-06-26
- 작성자: Kimgyuilli

## 맥락

[ADR-0003](0003-auth-session-transport-model.md)은 모바일·웹 공통 세션 모델을 access +
refresh로 정했다. 이어서 refresh token을 어떻게 관리할지 결정해야 했다.

결정 축은 두 가지다.

1. refresh token을 stateless token으로 둘지, 서버 저장형 token으로 둘지
2. 서버 저장형이면 1차 저장소를 PostgreSQL로 둘지 Redis로 둘지

refresh token은 access token보다 긴 수명을 가지며, 사용자 세션을 연장할 수 있는 권한이다.
따라서 단순 검증 비용뿐 아니라 로그아웃, 회전(rotation), 재사용 감지, 디바이스별 세션 관리,
장애 시 세션 보존성을 함께 봐야 한다.

## 결정

**Refresh token은 서버 저장형으로 관리하고, 1차 저장소는 PostgreSQL로 한다.**

1. 서버는 refresh token 원문을 저장하지 않고, 충분히 긴 랜덤 token의 해시와 상태만 저장한다.
2. refresh token 저장 정보는 세션 원장으로 본다. 최소 상태는 `user_id`, `token_hash`,
   `client_type`, `device`, `expires_at`, 폐기 상태다.
3. refresh는 회전한다. 성공한 refresh 요청은 기존 token을 폐기하고 새 refresh token을 발급한다.
4. 이미 폐기된 refresh token이 다시 들어오면 재사용 감지로 보고, 같은 세션 범위의 활성 refresh
   token을 폐기할 수 있게 한다.
5. auth 내부에는 refresh token 저장소 port를 둔다. 초기 구현체는 JPA/PostgreSQL이며, 향후
   Redis 전환이나 보조 저장소 추가가 필요해지면 구현체를 교체할 수 있게 한다.

Redis는 지금 도입하지 않는다. access token blacklist, rate limit, refresh lookup 병목, 또는
세션 상태의 TTL 기반 처리가 실제 요구로 확인되면 별도 ADR로 도입한다.

## 대안

### Stateless refresh token

서버가 refresh token을 JWT처럼 자체 검증 가능한 token으로 발급하고, 서명과 만료만 검증하는
방식이다.

장점은 구현이 단순하고 refresh 요청마다 저장소 조회가 없다는 점이다. 서버 인스턴스는 signing
key만 공유하면 되므로 수평 확장도 쉽다.

그러나 개별 로그아웃, 디바이스별 세션 종료, 강제 폐기, refresh rotation, 재사용 감지를 제대로
하려면 결국 blacklist나 token version 저장소가 필요하다. 그러면 stateless의 단순성이 줄어든다.
Momens의 refresh token은 단순한 긴 수명 증표가 아니라 서버가 회수·회전할 수 있는 세션 권한으로
보는 것이 맞으므로 기각한다.

### Redis를 1차 저장소로 사용

Refresh token 상태를 Redis key/value와 TTL로 관리하는 방식이다.

장점은 조회가 빠르고 TTL cleanup이 자연스럽다는 점이다. access token blacklist, rate limit,
일시적 denylist 같은 인증 보조 상태와도 궁합이 좋다.

그러나 Redis를 1차 저장소로 두면 persistence, HA, eviction policy, 백업/복구, local/test/CI
인프라가 모두 인증 세션의 필수 운영 정책이 된다. Redis 장애, flush, 잘못된 eviction은 대량
재로그인으로 이어질 수 있다. 또한 세션 이력·폐기 사유·rotation 이력을 남기려면 별도 원장이 다시
필요하다.

초기 제품 단계에서는 refresh token을 durable한 세션 원장으로 관리하는 가치가 속도보다 크다.
Refresh 요청은 access token 검증처럼 매 요청 발생하지 않으므로 PostgreSQL 부하도 감당 가능한
범위로 본다. Redis는 실제 병목이나 TTL 기반 보조 상태 요구가 생긴 뒤 도입한다.

## 결과

- 로그아웃은 클라이언트 로컬 삭제에만 의존하지 않고 서버 저장 상태 폐기로 반영한다.
- Refresh rotation과 재사용 감지를 구현할 수 있다.
- 디바이스·클라이언트 타입 단위 세션 관리로 확장할 수 있다.
- PostgreSQL 테이블과 Flyway migration이 필요하다.
- 만료된 refresh token 정리 정책이 필요하다. 초기에는 조회 조건과 운영 cleanup으로 시작하고,
  필요 시 별도 scheduled cleanup을 추가한다.
- 향후 Redis 전환 비용을 줄이기 위해 auth 내부 저장소 port를 둔다. 단, 이 port는 범용 세션
  저장소가 아니라 refresh token use case 전용으로 제한한다.
