# 0016. 사용자 신원 식별 키를 이메일에서 Google `sub`으로 전환

- 상태: Proposed
- 날짜: 2026-08-09
- 작성자: 신진수

## 맥락

모바일 로그인에서는 Google ID 토큰을 검증한 뒤 사용자를 조회하거나 생성한다. 이때 다시 로그인한 사용자가 우리 DB의
어떤 사용자에 해당하는지 식별하는 기준으로 **이메일**을 사용하고 있다.

현재 실행 흐름을 살펴보면 다음과 같다. `AuthService.loginWithGoogleToken`은 토큰 검증 결과에서 이메일, 이름, 프로필
이미지 URL만 추출해 `userService.findOrCreate(email, name, picture)`로 전달하고, `UserRepository.upsertByEmail`은
이를 `INSERT ... ON CONFLICT (email) DO UPDATE`로 처리한다.

이 구조는 레거시를 기준으로 구현한 결과이다. `momens-api`의 `FindOrCreateUser`도 `repo.FindByEmail`로 사용자를
조회하고 있으며, `users` 테이블에는 provider나 Google 사용자 식별자를 저장하는 컬럼이 없다.

여기서 주목해야 할 부분은 이메일이 우리 서버에서 관리하는 값이 아니라 Google이 관리하는 값이라는 점이다. 또한, Google
공식 문서에서 **사용자를 식별할 때 이메일 대신 `sub`(Subject)라는 고유 ID 값을 사용하라**는 기술 지침이 존재함을
확인했다.

> An identifier for the user, unique among all Google Accounts and never reused.
> Always use the `sub` field as it is unique to a Google Account even if the user changes their email address.

https://developers.google.com/identity/openid-connect/openid-connect#obtainuserinfo

이메일 변경은 드문 케이스이긴 하지만 실제로 발생할 수 있다. Google 계정 도움말에 따르면 회사나 학교 계정은 관리자가
이메일 주소를 변경할 수 있고, 개인 `gmail.com` 계정도 12개월에 한 번, 최대 세 번까지 다른 `gmail.com` 주소로 변경할 수
있다. 우리 팀 사용자 대부분이 회사 계정을 사용한다는 점을 고려하면, 도메인 변경이나 개명 등으로 관리자가 이메일을
변경하는 상황도 충분히 고려할 수 있다.

https://support.google.com/accounts/answer/19870

현재 구조에서는 동일한 사용자가 변경된 이메일로 로그인하면 `ON CONFLICT (email)`이 동작하지 않아 새로운 `users` 행이
생성된다. 사용자 ID는 워크스페이스 멤버십(`workspace_members.user_id`), 프로젝트 소유자(`projects.owner_id`), 태스크
담당자(`tasks.assignee_id`)가 FK로 참조하고 있기 때문에, 새 행이 생성되는 시점에서 기존 소속과 담당 관계가 모두 끊기게
된다. 이때, 사용자가 직접 복구할 방법은 없으며, 운영자가 데이터를 수동으로 수정해야 한다.

따라서 이번 ADR에서는 다음 세 가지를 결정한다.

1. 로그인 시 사용자를 식별할 키를 어떻게 정의할 지에 대한 판단
2. 해당 키를 `users` 컬럼에 둘지, 별도 테이블로 분리할 지에 대한 판단
3. 기존 사용자를 새로운 식별 체계로 어떻게 전환할 지에 대한 판단

## 결정

로그인 시 사용자를 식별하는 키를 이메일에서 발급자(provider)와 발급자가 부여한 사용자 식별자의 조합으로 변경하고,
이를 저장하기 위해 `user_identities` 테이블을 추가한다.

- **식별 키는 `(provider, provider_user_id)`로 정의한다.** Google의 `sub`은 계정마다 유일하고 재사용되지 않으며,
  이메일이 변경되어도 유지된다. 우리가 필요한 것은 "같은 사용자임을 판별하는 것"이므로 `sub`을 식별 키로 사용하는 것이
  적절하다. `provider`를 함께 두는 이유는 발급자가 다르면 동일한 값이라도 다른 사용자를 의미할 수 있기 때문이다.

- **식별 정보는 별도 테이블로 관리한다.** 스키마는 다음과 같다.

  ```sql
  CREATE TABLE user_identities (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      provider TEXT NOT NULL,
      provider_user_id TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE (provider, provider_user_id)
  );
  ```

  한 사용자가 여러 로그인 수단을 가질 수 있는 구조이기 때문에 `users` 단일 컬럼으로는 표현이 어렵다. UNIQUE 제약은
  동일한 Google 계정이 서로 다른 사용자에 중복으로 연결되는 상황을 DB 레벨에서 방지한다.

- **컬럼명은 `provider_user_id`로 사용한다.** ADR-0003에서 우리 access token의 `sub`을 내부 사용자 ID로 정의했기
  때문에, `sub`이라는 이름을 쓰면 토큰의 `sub`과 provider의 `sub`이 혼동될 수 있다. 이를 피하기 위해 의미가 명확한
  `provider_user_id`를 사용한다.

- **`users.email`은 화면 표시 용도로 사용하는 것으로 변경하고, 로그인 시마다 최신값으로 갱신한다.** 이름과 프로필
  이미지도 로그인 시마다 갱신하고 있으므로 동일한 정책을 적용한다. 현재로서는 사용자가 앱에서 이메일을 직접 수정하는
  기능은 제공하지 않는다.

- **기존 사용자는 이메일 기반으로 한 번만 identity를 생성한다.** 기존 사용자에는 `sub` 정보가 없기 때문에, 전환 이후 첫
  로그인 시 `(google, sub)` 조회 결과가 없으면 이메일로 `users`를 조회해 identity를 생성한다. 이후부터는 identity
  기준으로만 사용자를 조회한다.

- **HTTP 계약은 변경하지 않는다.** `POST /api/auth/google/token` 요청과 응답, 발급되는 토큰,
  `GET /api/mobile/bootstrap` 응답 모두 유지한다. 변경은 서버 내부의 사용자 조회 로직에만 국한되므로 클라이언트 수정
  또한 필요하지 않다.

## 대안

- **이메일을 식별 키로 유지한다.** Google 로그인만 사용하는 동안에는 정상적으로 동작한다. 하지만 이메일이 변경되는
  시점에서 동일 사용자가 신규 계정으로 처리되어 소속과 담당 데이터가 분리된다. 발생 빈도는 낮더라도 복구가 수동 작업에
  의존하게 되므로 운영 리소스가 발생한다.

- **`users`에 `google_sub` 컬럼을 추가한다.** 변경 범위가 작고 구현 또한 단순하다. 하지만 로그인 수단이 늘어날 때마다
  provider별 컬럼이 계속 추가되고, Google 등 여러 계정을 하나의 사용자에 연결하는 구조를 컬럼으로 표현하기 어렵다.
  모델의 확장성을 고려하여 제외했다.

- **Linear처럼 이메일 변경 기능을 제공한다.** 기존 이메일과 신규 이메일 모두 확인 메일을 보내 소유권을 검증하는
  방식이다. 하지만 우리는 Google이 신원을 보증하는 구조이며 이메일로 직접 로그인하지 않기 때문에, 이 과정을 추가해도
  Google이 관리하는 원본 이메일을 변경할 수 없다. 결과적으로 인프라 또한 복잡해진다.

- **사용자가 늘어난 뒤에 전환한다.** 현재 장애가 발생한 상황은 아니므로 후속 작업으로 보류할 수 있다. 하지만 전환이
  늦어질수록 이메일 기반으로 identity를 매핑해야 하는 사용자가 계속 늘어나고, 그 사이 이메일 변경이 발생하면 잘못된
  매핑이 발생할 위험도 커진다. 현재 시점이 전환 비용과 리스크가 적다고 판단된다.

## 결과

- Google 이메일이 변경되더라도 동일 사용자로 계속 인식되며, DB의 이메일은 다음 로그인 시 최신값으로 갱신된다.
- 로그인 과정에 조회가 하나 추가되지만 `(provider, provider_user_id)` UNIQUE 인덱스를 사용하므로 비용은 크지 않다.
- API 계약은 변경되지 않아 클라이언트 수정이 필요 없다.
- 향후 Google 외 다른 로그인 수단을 추가할 수 있는 구조가 된다. 하지만 실제로 추가하려면 계정 연결 API와 인증 수단 보호
  로직이 필요하며, 이는 이번 범위에는 포함하지 않는다.
- `users.email`의 UNIQUE 제약 유지 여부는 이번 ADR에서 결정하지 않는다. 현재는 레거시 `momens-api`와 웹에서 이메일
  기반 조회가 사용되고 있으므로, 해당 의존성을 확인한 뒤 별도로 정리한다.
- `user_identities`는 신규 테이블이므로 운영 환경에서는 새 서버 Flyway로 생성할 수 없다.
  `docs/rules/persistence.md` 기준에 따라 레거시 마이그레이션으로 추가해야 하며, 배포 및 스키마 담당자인 규일과 적용
  순서를 먼저 조율한다.
- 기존 데이터 전환은 첫 로그인 시 자연스럽게 처리되므로 별도로 누락된 과거 데이터를 처리하는 로직은 두지 않는다. 전환
  이후 일정 기간 동안 identity가 없는 사용자를 점검해 데이터 정합성을 확인한다.
- 프로필 화면에 이메일을 표시할지, 표시한다면 수정할 수 없는 값으로 둘지는 기획과 논의한다. 프로필 화면은 2026-07-03
  7차 기획 회의에서 미정 항목으로 남아 있다. 따라서 화면 구성은 기획에서 확인해야 할 항목으로 남겨둔다.