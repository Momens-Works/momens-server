# 0016. 사용자 신원 식별 키를 이메일에서 Google `sub`으로 전환

- 상태: Accepted
- 날짜: 2026-08-09
- 작성자: jsshin8128

## 맥락

우리 서버는 Google 로그인을 두 가지 경로로 처리한다. 모바일에서는 앱을 통해 전달받은 구글 ID 토큰을
검증하고(`AuthService.loginWithGoogleToken`), 웹에서는 authorization code를 토큰으로 교환한 뒤 userinfo를
조회한다(`WebAuthService.completeLogin`). 현재 두 경로 모두 다시 로그인한 사용자가 DB의 어떤 사용자에
해당하는지 이메일을 기준으로 식별한다.

현재 로그인 흐름은 다음과 같다. `AuthService.loginWithGoogleToken`은 토큰 검증 결과에서 이메일, 이름,
프로필 이미지 URL을 추출해 `userService.findOrCreate(email, name, picture)`에 전달한다.
`WebAuthService.completeLogin` 또한 userinfo 응답에서 같은 세 값을 추출해 동일한 API를 호출한다. 전달된
사용자 정보는 `UserRepository.upsertByEmail`에서 `INSERT ... ON CONFLICT (email) DO UPDATE`로 처리한다.

한편, upsert 방식을 통해 같은 신규 이메일로 최초 로그인 요청이 동시에 들어오더라도 하나의 사용자 행으로
수렴하도록 원자성을 보장하고 있다. 따라서 식별 키를 변경하더라도 이 동시성에 대한 설계는 유지해야 한다.

현재 구조는 레거시를 기준으로 구현한 결과이다.

`teams` 레포지토리의 2026-06-26 4차 기획 회의에서 모바일 인증 방침을 "계정은 이메일 기반으로 통합한다(구글
계정 이메일과 기존 계정 매핑)"으로 정했고, 후속 작업으로 "이메일을 기존 계정과 정확히 매핑하는 로직"을
남겼다. 따라서 이번 ADR에서는 Google 로그인을 기존 계정에 연결한다는 취지는 유지하되, 계정을 매핑하는
기준만 변경한다.

여기서 주목할 부분은 이메일이 우리 서버에서 관리하는 값이 아니라 Google에서 관리하는 값이라는 점이다.
Google 공식 문서에서는 사용자를 식별할 때 이메일 대신 `sub`(Subject)라는 고유 식별자를 사용할 것을
권장한다.

> An identifier for the user, unique among all Google Accounts and never reused.
> Always use the `sub` field as it is unique to a Google Account even if the user changes their email address.

[Google OpenID Connect 문서](https://developers.google.com/identity/openid-connect/openid-connect#obtainuserinfo)

이메일 변경은 드물지만 실제로 발생할 수 있다. Google 계정 도움말에 따르면 회사나 학교 계정은 관리자가
이메일 주소를 변경할 수 있고, 개인 `gmail.com` 계정도 12개월에 한 번, 최대 세 번까지 다른 `gmail.com`
주소로 변경할 수 있다. 우리 팀 사용자의 대부분이 회사 계정을 사용한다는 점을 고려하면, 도메인 변경이나
개명 등으로 관리자가 이메일 주소를 변경하는 상황도 충분히 발생할 수 있다.

[Google 계정 이메일 변경 안내](https://support.google.com/accounts/answer/19870)

현재 구조에서는 동일한 사용자가 변경된 이메일로 로그인하면 `ON CONFLICT (email)`이 동작하지 않아 새로운
`users` 행이 생성된다. 사용자 ID를 참조하는 데이터는 다음 두 종류로 나뉜다.

- FK로 참조하는 데이터
  - 워크스페이스 멤버십(`workspace_members.user_id`)
  - 프로젝트 소유자(`projects.owner_id`)
  - 태스크 담당자(`tasks.assignee_id`)
- FK 없이 사용자 ID 값만 저장하는 데이터
  - refresh token(`refresh_tokens.user_id`)
  - 푸시 설치 정보(`push_installations.user_id`)
  - 푸시 발송 이력(`push_deliveries.target_user_id`)
  - 시그널 처리 이력(`signal_actions.processed_by_user_id`)

새로운 사용자 행이 생성되면 FK로 연결된 관계는 끊어진다. FK 없이 사용자 ID만 저장한 데이터 역시 기존
사용자 ID를 사용하게 되기 때문에 새로운 사용자와 연결되지 않게 된다. 따라서 사용자가 직접 복구할 방법은
없으며, 운영자가 데이터를 수동으로 수정해야 한다.

이번 결정의 적용 범위에는 한계가 있다. 현재 운영 환경은 레거시 `momens-api`와 공유 DB를 사용하는
전환기이며([데이터](../rules/persistence.md)), 두 서버가 함께 배포되어 있다. 레거시 `users`에는 provider
컬럼이 없으므로 레거시 서버에서는 새로운 식별 체계를 사용할 수 없다. 따라서 레거시 이관 완료 전까지 이번
결정으로 해결할 수 있는 범위는 우리 서버의 로그인 경로로 한정된다.

이번 ADR에서는 다음 다섯 가지를 결정한다.

1. 로그인 시 사용자를 식별할 키
2. 식별 키를 `users` 컬럼에 둘지 별도 테이블로 분리할지와, 별도 테이블로 분리할 경우 해당 테이블을 소유할
   모듈
3. 기존 사용자를 새로운 식별 체계로 전환하는 방법
4. 신규 사용자 생성 시 현재의 동시성 보장을 유지하는 방법
5. `users.email`의 용도와 UNIQUE 제약 처리 방안

## 결정

로그인 시 사용자를 식별하는 키를 이메일에서 발급자(provider)와 발급자가 부여한 사용자 식별자의 조합으로
변경하고, 이를 저장하기 위해 `user_identities` 테이블을 추가한다. 이 결정은 모바일 ID 토큰 경로와 웹
authorization code 경로를 포함한 이 서버의 모든 Google 로그인 경로에 적용한다.

- **식별 키는 `(provider, provider_user_id)`로 정의한다.** Google의 `sub`은 계정마다 고유하고 재사용되지
  않으며, 이메일이 변경되어도 유지된다. 우리에게 필요한 속성은 동일한 사용자인지를 안정적으로 판별하는
  것이므로 `sub`을 식별 키로 사용하는 것이 적절하다. `provider`를 함께 저장하는 이유는 발급자가 다르면 같은
  식별자 값이라도 서로 다른 사용자를 나타낼 수 있기 때문이다.

- **식별 정보는 별도 테이블로 관리하며, `user` 모듈이 소유한다.** 스키마는 다음과 같다.

  ```sql
  CREATE TABLE user_identities (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      provider TEXT NOT NULL CHECK (provider IN ('google')),
      provider_user_id TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE (provider, provider_user_id)
  );
  ```

  한 사용자가 여러 로그인 수단을 가질 수 있는 관계이므로 `users`의 단일 컬럼으로는 이를 표현하기 어렵다.
  UNIQUE 제약은 동일한 Google 계정이 서로 다른 사용자에게 중복으로 연결되는 상황을 DB 수준에서 방지한다.

  `provider`에 CHECK 제약을 두는 이유는 허용되지 않는 값을 거부해 잘못된 데이터가 저장되지 않게 하려는
  의도를 가지고 있기 때문이다. 같은 성격의 컬럼인 `signals.type`, `signal_actions.action_type`,
  `tasks.status`, `tasks.priority`, `push_installations.platform`에도 CHECK 제약을 사용하고 있다. 로그인
  수단이 추가되면 허용되는 값도 함께 추가한다.

  `user` 모듈이 테이블을 소유하는 이유는 신규 사용자를 생성할 때 `users` 한 행과 `user_identities` 한 행이
  함께 생성되어야 하기 때문이다. 테이블 이름만 보면 identity가 인증 관심사처럼 보여 `auth` 모듈이 소유하는
  구조도 생각할 수 있다. 그러나 `auth`가 소유하면 `users` 생성은 `user`의 public API를 통해 처리하고
  identity 생성은 `auth`에서 처리하게 되어, 두 행의 생성이 서로 다른 모듈로 분산된다. 중간에 실패하면
  identity가 없는 `users` 행이 남을 수 있고, 신규 사용자 생성의 동시성 보장도 한 곳에서 처리하기 어렵다.
  이는 모듈 경계를 넘는 트랜잭션 참여를 최소화한다는 규칙([아키텍처](../rules/architecture.md))에도 맞지
  않는다.

  `user` 모듈이 소유하면 `user_id` FK 관계가 모듈 내부에서 닫힌다는 장점도 있다. `auth`가 소유했다면
  `auth`의 `refresh_tokens`가 `users`에 FK를 두지 않은 것과 같은 이유로 `user_identities`에서도 FK를
  제외해야 한다. 마이그레이션 역시 `modules/user`에 둔다([데이터](../rules/persistence.md)).

  `auth`는 지금과 같이 `user` 모듈의 public API에만 의존한다. 기존 `findOrCreate`는 검증이 끝난 `provider`와
  `provider_user_id`를 함께 받는 형태로 변경한다. 이후 계정 연결 API가 `auth`에 추가되더라도, 해당 API에서
  `user` 모듈의 public API를 호출하면 된다.

- **컬럼명은 `provider_user_id`로 사용한다.** ADR-0003에서 우리 access token의 `sub`을 내부 사용자 ID로
  정의했다. 컬럼명까지 `sub`으로 지정하면 내부 토큰의 `sub`과 외부 provider가 발급한 `sub`을 서로 혼동할 수
  있으므로, 의미가 명확한 `provider_user_id`를 사용한다.

- **신규 사용자 생성은 하나의 트랜잭션에서 처리한다.** `users` 삽입과 `user_identities` 삽입을 같은
  트랜잭션에 둔다. identity 삽입은 `ON CONFLICT (provider, provider_user_id) DO NOTHING`을 사용하고, 충돌이
  발생하면 identity를 다시 조회해 기존 사용자로 연결한다.

  UNIQUE 제약만 추가하는 것으로는 충분하지 않다. 동시 요청 두 건이 각각 `users` 행을 생성한 뒤 한쪽 요청이
  identity 삽입에서 충돌하면, identity가 없는 `users` 행이 남을 수 있기 때문이다. 이는 기존
  `upsertByEmail`이 보장하던 동시 로그인 요청 시 방어 로직을 새로운 구조에서도 유지하기 위한 결정이다.

- **기존 사용자는 이메일을 기준으로 한 번만 매칭해 identity를 생성한다.** 기존 사용자에게는 `sub`을 저장한
  이력이 없으므로, 전환 후 첫 로그인에서 `(google, sub)` 조회 결과가 없으면 이메일로 `users`를 조회한 뒤
  identity를 생성한다. 이후 로그인부터는 identity를 기준으로 사용자를 조회한다. 이 fallback은 모바일과 웹
  로그인 경로에 모두 적용한다.

- **`users.email`은 식별 키로 사용하지 않으며, 로그인할 때마다 최신값으로 갱신한다.** 이름과 프로필 이미지
  URL도 현재 로그인 시마다 갱신하고 있으므로 이메일에도 같은 정책을 적용한다. 현재 사용자가 앱에서 이메일을
  직접 수정하는 기능은 제공하지 않는다.

  한편, 레거시 `momens-api`의 워크스페이스 초대 수락 로직의 경우 초대에 저장된 이메일과 `users.email`을
  비교해 수락 여부를 판단한다. 따라서 사용자의 이메일이 갱신되면 이전 이메일 주소로 발송된 대기 중인 초대는
  더 이상 매칭되지 않는다. 이 의존성은 레거시 이관 완료 시점까지 유지된다.

- **`users.email`의 UNIQUE 제약을 제거한다.** 이메일을 앞으로는 식별 키로 사용하지 않으므로 유일성을
  강제하지 않아도 된다. 또한 UNIQUE 제약을 유지하면 이메일을 최신값으로 갱신하는 과정이 실패할 수 있다.

  이메일 변경으로 이미 중복된 `users` 행이 생성된 사용자가 전환 후 로그인하면, 기존 사용자 행의 이메일을 새
  이메일로 갱신하는 시점에 중복 행과 충돌한다. 반면, `NOT NULL` 제약은 유지한다.

- **HTTP 계약은 변경하지 않는다.** `POST /api/auth/google/token`, `GET /api/auth/google/login`,
  `GET /api/auth/google/callback`의 요청과 응답, 서버가 발급하는 토큰, `GET /api/mobile/bootstrap`의 응답은
  모두 유지한다. 변경 범위는 서버 내부의 사용자 조회 로직으로 한정되므로 클라이언트 수정은 필요하지 않다.

## 대안

- **이메일을 식별 키로 유지한다.** Google 로그인만 사용하는 동안에는 정상적으로 동작한다. 그러나 이메일이
  변경되면 동일한 사용자가 신규 계정으로 처리되어 기존 소속과 담당 데이터가 분리된다. 발생 빈도는 낮더라도
  복구가 수동 작업에 의존하므로 운영 리소스가 증가한다고 판단했다.

- **`users`에 `google_sub` 컬럼을 추가한다.** 변경 범위가 작고 구현도 단순하다. 그러나 로그인 수단이 늘어날
  때마다 provider별 컬럼이 계속 추가된다. 또한 Google 계정을 포함한 여러 로그인 수단을 하나의 사용자에게
  연결하는 관계를 컬럼만으로 표현하기 어렵다. 모델의 확장성을 고려해 제외했다.

- **`user_identities`를 `auth` 모듈이 소유한다.** 로그인 수단은 인증 관심사이므로 모듈 이름만 보면
  자연스럽고, 이후 계정 연결 API도 `auth`에 추가될 가능성이 높다. 그러나 신규 사용자 생성이 `users`와
  `user_identities`를 소유한 두 모듈에 걸쳐 처리되며, 중간에 실패하면 identity가 없는 `users` 행이 남을 수
  있다. 계정 연결 API는 `auth`가 `user` 모듈의 public API를 호출하는 방식으로 구현할 수 있으므로 이러한
  단점을 감수할 이유가 없다고 판단했다.

- **`users.email`의 UNIQUE 제약을 유지한다.** 레거시에서 이메일당 사용자 한 명을 전제로 작성된 코드를
  유지할 수 있다. 그러나 이메일을 갱신할 때 기존 중복 사용자 행과 충돌해 로그인이 실패할 수 있다. 즉,
  유일성을 유지하기 위해 이메일 갱신을 포기하면 DB에 이전 이메일이 남아 표시 정보가 불일치하고 초대 매칭이
  실패하게 된다.

- **Linear처럼 이메일 변경 기능을 제공한다.** 기존 이메일과 새 이메일에 각각 확인 메일을 보내 소유권을
  검증하는 방식이다. 그러나 우리는 Google이 사용자의 신원을 보증하며 이메일로 직접 로그인하지 않는다. 이
  절차를 추가하더라도 Google이 관리하는 원본 이메일을 변경할 수 없고, 메일 발송을 위한 인프라 또한 추가되어야
  한다. 아키텍처가 복잡해지므로 제외했다.

- **사용자가 늘어난 뒤 전환한다.** 현재 실제 장애가 발생한 것은 아니므로 후속 작업으로 미룰 수 있다. 그러나
  전환이 늦어질수록 이메일을 기준으로 identity를 연결해야 하는 사용자가 계속 늘어난다. 그사이에 이메일
  변경이 발생하면 실제 사용자와 `users` 행이 어긋나 잘못 매핑될 위험도 커진다. 따라서 현재가 전환 비용과
  위험이 낮은 시점이라고 판단했다.

## 결과

- Google 계정의 이메일이 변경되어도 동일한 사용자로 계속 인식하며, DB에 저장된 이메일은 다음 로그인 시
  최신값으로 갱신된다.
- 로그인 과정에 조회가 추가되지만 `(provider, provider_user_id)` UNIQUE 인덱스를 사용하므로 조회 비용은 크지
  않다.
- API 계약을 유지하므로 클라이언트 수정은 필요하지 않다.
- 향후 Google 외의 로그인 수단을 추가할 수 있는 구조가 마련된다. 하지만 실제로 로그인 수단을 추가하려면 계정
  연결 API와 인증 수단 보호 로직이 필요하며, 이는 이번 작업 범위에 포함하지 않는다.
- 이번 결정으로 해결되는 범위는 레거시 이관 완료 전까지 이 서버의 로그인 경로로 한정된다. 레거시
  `momens-api`의 `FindOrCreateUser`는 계속 이메일을 기준으로 사용자를 조회하고, `users`에도 provider 컬럼이
  없기 때문에 레거시를 통해 로그인한 사용자는 identity 없이 생성될 수 있다. 이메일이 변경되면 여전히 새로운
  `users` 행이 생성된다. 따라서 이메일 fallback은 레거시 이관 완료 전까지 유지해야 한다.
- dev 전용 토큰 발급 기능인 `DevTokenService`와 데모 시드는 Google 로그인을 거치지 않고 사용자를 생성하므로
  identity가 없는 `users` 행을 만든다. 두 기능 모두 dev와 demo 환경에서만 사용하므로 운영에는 영향을 주지
  않는다. 따라서 점검 대상에서 제외한다.
- `users.email`의 UNIQUE 제약을 제거하면 이메일당 사용자 한 명을 전제로 조회하는 레거시 코드가 중복 행이
  발생한 경우 어떤 사용자를 반환할지 보장할 수 없다. 레거시의 로그인 조회와 워크스페이스 초대 조회가 이에
  해당한다. 중복 행은 기존에 생성된 데이터에만 존재하고 전환 이후에는 더 늘어나지 않지만, 레거시 이관 완료
  전까지 문제가 발생할 수 있다. 리스크 항목이므로 MOM-0831에서 기존 중복 현황을 먼저 확인한 뒤 작업 여부를
  결정한다.
- 이메일 갱신으로 대기 중인 초대를 수락하지 못할 수 있다. 레거시 초대는 발송 당시의 이메일로 사용자를
  매칭하므로, 이메일이 변경된 사용자는 새 주소로 초대를 다시 받아야 한다. 레거시 이관 완료 이후 초대 기능을
  새 서버로 이관할 때 매칭 기준을 사용자 ID로 변경할지도 함께 결정한다.
- 2026-06-26 기획 결정에는 "이메일 기반 계정 통합"으로 기록되어 있으므로 `teams` 레포지토리의 관련 문서에도
  이번 변경을 반영해야 한다. 계정을 하나로 통합한다는 기존 취지는 유지하고 매핑 기준만 변경한다는 점을 기획과
  함께 논의한다.
- `user_identities` 테이블 생성과 `users.email` UNIQUE 제약 제거는 모두 운영 스키마를 변경하는 작업이므로 새
  서버의 Flyway로 처리할 수 없다. `docs/rules/persistence.md`에 따라 레거시 마이그레이션으로 반영하며,
  MOM-0831에서 처리한다. 운영 환경에는 `ddl-auto=validate`가 적용되므로 서버 구현인 MOM-0830을 배포하기 전에
  마이그레이션을 먼저 적용해야 한다. 따라서 배포 및 스키마 담당자인 규일과 적용 순서를 먼저 조율한다.
- 기존 사용자 데이터는 첫 로그인 과정에서 자연스럽게 전환되므로, 과거 데이터의 누락분을 별도로 처리하는
  로직은 두지 않는다.
- `user` 모듈의 책임이 프로필 관리에서 로그인 수단 관리까지 확장된다. 이에 따라
  `docs/design/module-map.md`의 `user` 항목과 `findOrCreate` public API 설명을 MOM-0830에서 함께 갱신한다.
- 프로필 화면에 이메일을 표시할지와, 표시할 경우 수정할 수 없는 값으로 제공할지는 기획과 논의한다. 프로필
  화면은 2026-07-03 7차 기획 회의에서도 미정 항목으로 남아 있으므로, 화면 구성은 기획 확인이 필요한 사항으로
  남겨 둔다.
