# 모바일 API 명세

이 문서는 모바일 MVP에서 앱이 호출하는 HTTP API 계약만 정리합니다.

## 공통

- Base URL: `/api`
- 모바일 기능 API: `/api/mobile/*`
- 인증 API: `/api/auth/*`
- API version header: `API-Version: 1`
- 인증 header: `Authorization: Bearer {access_token}`
- Content-Type: `application/json`
- 필드 표기: `snake_case`
- 성공 응답: 별도 wrapper 없이 응답 DTO 그대로 반환

인증 API 중 로그인과 토큰 갱신은 `Authorization` 헤더가 필요하지 않습니다.

### 에러 응답

```json
{
  "error": {
    "code": "COMMON_VALIDATION_FAILED",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

## Enum

### Signal

| 이름 | 값 |
| --- | --- |
| `type` | `risk`, `decision`, `change`, `question` |
| `evidence.source` | `slack`, `github`, `figma`, `notion`, `file` |
| `action_command` | `convert-to-task`, `dismiss` |
| `action` | `convert_to_task`, `dismiss` |
| `primary_action` | `convert-to-task` |

`action_command`는 상세 응답의 `actions[]`와 path segment에 쓰는 명령 값입니다. `action`은 action 처리 결과와
저장 ledger의 enum 값입니다.

Signal type 기반 화면 라벨은 앱이 다음처럼 파생합니다. 이 라벨은 처리 상태나 목록 필터가 아닙니다.

| type | 화면 라벨 |
| --- | --- |
| `risk`, `change` | `Needs action` |
| `decision` | `Needs review` |
| `question` | `Needs decision` |

화면설계서의 Signal 카테고리 표시는 같은 `type`에서 파생합니다. 특히 VOC는 별도 API enum이 아니라
`change` type의 화면 표시명입니다.

| type | 카테고리 표시 |
| --- | --- |
| `risk` | `Risk` |
| `decision` | `Decision` |
| `change` | `VOC` |
| `question` | `Question` |

### Task

| 이름 | 값 |
| --- | --- |
| `group_key` | `todo`, `in_progress`, `done` |
| `priority` | `high`, `medium`, `low` |
| `role` | `pm`, `design`, `backend`, `frontend` (하나만 선택. android, qa는 2026-07-08 기획 확정으로 폐기) |

### Membership

| 이름 | 값 |
| --- | --- |
| `role` | `owner`, `admin`, `member` |

### Material

| 이름 | 값 |
| --- | --- |
| `kind` | `doc`, `node`, `msg` |

## 인증

### POST /api/auth/google/token

Google ID token을 Momens access/refresh token으로 교환합니다.

#### Request

```json
{
  "id_token": "google-id-token",
  "device": "iPhone 15 Pro"
}
```

#### Response 200

```json
{
  "access_token": "access-token",
  "refresh_token": "refresh-token",
  "token_type": "Bearer",
  "expires_in": 900
}
```

#### Errors

- `AUTH_GOOGLE_TOKEN_INVALID`
- `AUTH_GOOGLE_EMAIL_NOT_VERIFIED`
- `COMMON_VALIDATION_FAILED`

### POST /api/auth/refresh

Refresh token으로 access/refresh token을 재발급합니다.

#### Request

```json
{
  "refresh_token": "refresh-token"
}
```

#### Response 200

```json
{
  "access_token": "new-access-token",
  "refresh_token": "new-refresh-token",
  "token_type": "Bearer",
  "expires_in": 900
}
```

#### Errors

- `AUTH_REFRESH_TOKEN_INVALID`
- `COMMON_VALIDATION_FAILED`

### POST /api/auth/logout

Refresh token을 폐기합니다.

#### Request

```json
{
  "refresh_token": "refresh-token"
}
```

#### Response 200

```json
{
  "message": "logged out"
}
```

#### Errors

- `AUTH_REFRESH_TOKEN_INVALID`
- `COMMON_VALIDATION_FAILED`

## 모바일 진입

### GET /api/mobile/bootstrap

로그인 후 앱 진입에 필요한 사용자 정보와 프로젝트 목록을 조회합니다.

- `projects`는 접근 가능한 프로젝트를 생성 최신순으로 담습니다. `role`은 소속 workspace 멤버십 role입니다.
- `default_project_id`는 임의의 프로젝트 1개면 충분하다고 기획이 확인했고(2026-07-04), 서버는
  접근 가능한 프로젝트 중 가장 최근에 만든 것을 선택합니다.
- 접근 가능한 프로젝트가 없으면 `200`으로 `default_project_id`는 `null`, `projects`는 빈 배열을
  반환하고, 빈 화면 처리는 앱이 담당합니다(2026-07-04 가결정, 기획 확인 후 확정).

#### Response 200

```json
{
  "me": {
    "id": "user-uuid",
    "name": "김민지",
    "avatar_url": null
  },
  "default_project_id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
  "projects": [
    {
      "id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
      "name": "Q2 Activation Readiness",
      "role": "member"
    }
  ]
}
```

#### Response 200 (접근 가능한 프로젝트 없음)

```json
{
  "me": {
    "id": "user-uuid",
    "name": "김민지",
    "avatar_url": null
  },
  "default_project_id": null,
  "projects": []
}
```

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `USER_NOT_FOUND` (유효한 토큰이지만 사용자가 삭제된 경우. `GET /api/me`와 같은 동작)

## 프로젝트

### GET /api/mobile/projects/{projectId}/members

태스크 담당자 선택에 사용할 프로젝트 멤버 목록을 조회합니다.

- `members`의 범위는 project가 속한 workspace의 멤버십입니다. 별도 project 멤버 테이블을 두지
  않는 권한 요구사항과 같은 기준입니다.
- 정렬은 이름 오름차순이고, 같은 이름은 id 오름차순으로 순서를 고정합니다(2026-07-04 가결정).
- `query`는 앞뒤 공백을 지운 뒤 이름 부분 일치로 거르고 대소문자를 무시합니다. 없거나 공백이면
  전체 멤버를 반환합니다(2026-07-04 가결정).
- `avatar_url`은 값이 없어도 `null`로 항상 포함합니다(bootstrap과 동일).
- 레거시 `GET /workspaces/:id/members`와 달리 email, role, 시각을 내리지 않습니다. 담당자 선택
  bottom sheet가 쓰는 값(id, 이름, 아바타)만 담는 신규 계약입니다(의도된 차이).

#### Query

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `query` | 아니오 | 이름 검색어 |

#### Response 200

```json
{
  "members": [
    {
      "id": "b9b1...e7",
      "name": "김민지",
      "avatar_url": null
    }
  ]
}
```

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `PROJECT_NOT_FOUND` (project가 없거나 삭제된 경우)
- `AUTH_FORBIDDEN` (project는 있지만 요청 사용자가 소속 workspace의 멤버가 아닌 경우)

## 시그널

### GET /api/mobile/projects/{projectId}/signals

프로젝트의 시그널 목록을 조회합니다.

MVP에서는 아직 처리되지 않은 시그널만 반환합니다. `convert-to-task` 또는 `dismiss`로 처리된 시그널을
다시 보는 inbox/필터 흐름은 MVP 이후로 둡니다.

정렬은 최신 Signal이 먼저 오도록 생성 시각 내림차순을 기본으로 합니다. 생성 시각이 같으면 id
내림차순으로 순서를 고정합니다. 수십 건 이상 누적될 때의 pagination/cursor 계약은 MVP 이후 확장으로
둡니다.

카드의 `Needs action`, `Needs review`, `Needs decision` 라벨은 응답의 `type`에서 앱이 파생합니다. 서버가
별도 처리 상태 필드를 내려주지 않습니다.

`impact`는 프로젝트에 미칠 영향 요약이며 목록 카드의 보조 문구로도 사용합니다. `impact`, `minsu_suggestion`은
worker/Minsu가 아직 생산하지 않았으면 `null`일 수 있습니다. 서버는 근거 없는 문구를 임의 생성하지 않습니다.

#### Response 200

```json
{
  "title": "오늘 확인해야 할 시그널",
  "description": "프로젝트의 의사결정에 영향을 줄 수 있는 변화입니다.",
  "signals": [
    {
      "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
      "project_id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
      "type": "risk",
      "title": "Android 13+ 권한 요청 플로우에서 이탈 가능성 발견",
      "impact": "MVP 완료율과 온보딩 품질에 영향을 줄 수 있습니다.",
      "minsu_suggestion": "내용이 들어갈 공간입니다"
    }
  ]
}
```

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `PROJECT_NOT_FOUND`
- `AUTH_FORBIDDEN`

### GET /api/mobile/signals/{signalId}

시그널 상세 bottom sheet에 필요한 정보를 조회합니다.

상세 상단의 `Needs action` 등 라벨은 Signal type 기반 표시 라벨이며, 처리 상태가 아닙니다.

#### Response 200

```json
{
  "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
  "project": {
    "id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
    "name": "Q2 Activation Readiness"
  },
  "type": "risk",
  "title": "Android 13+ 권한 요청 플로우에서 이탈 가능성 발견",
  "description": "Android 13 이상에서 권한 요청 타이밍이 늦어져 사용자가 기능 가치를 이해하기 전에 이탈할 수 있습니다.",
  "impact": "MVP 완료율과 온보딩 품질에 영향을 줄 수 있습니다.",
  "evidence": [
    {
      "id": "evidence-uuid",
      "source": "figma",
      "source_title": "권한 요청 화면 v2",
      "occurred_at": "2026-06-28T09:48:00+09:00",
      "summary": "설명 문구 변경으로 이탈 가능성이 있습니다.",
      "fields": [],
      "source_url": "https://..."
    }
  ],
  "minsu": {
    "suggestion": "내용이 들어갈 공간입니다",
    "task_draft": {
      "title": "Android 13+ 권한 요청 플로우 점검",
      "role": "frontend",
      "priority": "medium"
    }
  },
  "actions": ["convert-to-task", "dismiss"],
  "primary_action": "convert-to-task"
}
```

`description`은 Signal 상세 본문이고, `impact`는 프로젝트에 미칠 영향 요약입니다. `impact`와
`minsu.suggestion`은 worker/Minsu가 아직 생산하지 않았으면 `null`일 수 있습니다. `minsu.task_draft`는
worker/Minsu 산출물이 없으면 서버가 Signal title 기반 최소 초안을 제공할 수 있습니다.

`evidence[].fields`는 provider별 부가 필드를 위한 자리이며, MVP에서는 항상 빈 배열입니다. 근거 없는 값을
임의로 채우지 않고, source type별 필드 투영은 후속으로 둡니다.

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `SIGNAL_NOT_FOUND`
- `AUTH_FORBIDDEN`

### POST /api/mobile/signals/{signalId}/actions/convert-to-task

시그널을 태스크로 등록합니다.

#### Request

`title`은 생략하면 시그널 제목을, `priority`는 생략하면 `medium`을 씁니다. `role`은 서버가
채울 수 있는 값이 없어(시그널 상세의 `minsu.task_draft.roles`는 항상 빈 배열) 보내지 않으면
`COMMON_VALIDATION_FAILED`를 반환합니다 — 사실상 필수입니다. `title`은 값을 보낸다면 공백일
수 없습니다.

```json
{
  "title": "Android 13+ 권한 요청 플로우 점검",
  "role": "frontend",
  "priority": "medium"
}
```

#### Response 201

```json
{
  "task": {
    "id": "new-task-uuid",
    "title": "Android 13+ 권한 요청 플로우 점검",
    "status": "todo"
  },
  "signal": {
    "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
    "action": "convert_to_task"
  }
}
```

#### Response 200 (이미 처리된 신호 재요청)

같은 신호에 같은 액션을 재요청하면 새 task를 만들지 않고 기존 결과를 그대로 반환합니다(멱등).

```json
{
  "task": {
    "id": "existing-task-uuid",
    "title": "Android 13+ 권한 요청 플로우 점검",
    "status": "todo"
  },
  "signal": {
    "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
    "action": "convert_to_task"
  }
}
```

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `SIGNAL_NOT_FOUND`
- `SIGNAL_INVALID_STATE`
- `COMMON_VALIDATION_FAILED`
- `AUTH_FORBIDDEN`

### POST /api/mobile/signals/{signalId}/actions/dismiss

제안된 시그널을 MVP 흐름에서 수용하지 않고 목록에서 삭제 처리합니다. 모바일 화면의 버튼 라벨은 `삭제`지만
서버 action 이름은 `dismiss`입니다. 이 액션은 물리 삭제가 아니고 시그널이 잘못됐다고 확정하는 것도 아니라,
사용자가 현재 시그널을 task로 전환하지 않겠다는 처리 기록입니다. 삭제 처리한 시그널을 다시 보는 inbox
흐름은 MVP 이후로 둡니다.

#### Response 200

```json
{
  "signal": {
    "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
    "action": "dismiss"
  }
}
```

#### Response 200 (이미 같은 액션으로 처리된 신호 재요청)

같은 신호에 dismiss를 재요청하면 같은 결과를 멱등하게 반환합니다.

```json
{
  "signal": {
    "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
    "action": "dismiss"
  }
}
```

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `SIGNAL_NOT_FOUND`
- `SIGNAL_INVALID_STATE`
- `AUTH_FORBIDDEN`

`SIGNAL_INVALID_STATE`(409)는 이미 다른 액션으로 처리된 Signal에 요청하거나, 현재 상태에서 요청한 액션을
수행할 수 없는 경우에 반환합니다. 같은 액션 재요청은 위 `200` 멱등 응답으로 처리합니다.

## 브리프

### GET /api/mobile/projects/{projectId}/brief

프로젝트 브리프 화면에 필요한 정보를 조회합니다.

#### Query

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `signal_summary_filter` | 아니오 | `all`, `decisions`, `risks`, `questions`. 기본값은 `all` |
| `signal_summary_limit` | 아니오 | 요약 항목 수 제한 |

#### Response 200

```json
{
  "project": {
    "id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
    "name": "Q2 Activation Readiness",
    "target_date": "2026-06-30",
    "progress": 64,
    "summary": "내용이 들어갈 공간입니다"
  },
  "review_summary": {
    "title": "리뷰 요약",
    "body": "Android 권한 요청 이슈가 발견되었으며, 소셜 로그인은 MVP 범위에서 제외되었습니다."
  },
  "signal_summary": {
    "selected_filter": "all",
    "filters": [
      { "key": "all", "label": "전체", "count": 4 },
      { "key": "decisions", "label": "결정", "count": 2 },
      { "key": "risks", "label": "리스크", "count": 1 },
      { "key": "questions", "label": "질문", "count": 1 }
    ],
    "items": [
      {
        "id": "signal-or-memory-id",
        "type": "risk",
        "title": "Android 권한 요청 이슈",
        "summary": "MVP 완료율과 온보딩 품질에 영향을 줄 수 있습니다.",
        "source": "signal",
        "signal_id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1"
      }
    ]
  },
  "priorities": [
    {
      "rank": 1,
      "title": "이메일 회원가입 완료율 개선",
      "task_id": "27afd507-9c7f-4f0d-a2be-fcdab2477b19",
      "signal_id": null
    }
  ]
}
```

`signal_summary`는 `change`(VOC) 신호를 노출하지 않고 필터는 `all`, `decisions`, `risks`, `questions`만 둡니다.
`review_summary`는 worker/Minsu 산출물 후보로 MVP backing source가 없으면 `null`(또는 내부 값이 빈 상태)로
반환합니다(요구사항 명세 "합성/파생 필드 응답 정책" 참고).

#### Errors

- `PROJECT_NOT_FOUND`
- `AUTH_FORBIDDEN`

## 태스크

### GET /api/mobile/projects/{projectId}/tasks

프로젝트 태스크 보드를 조회합니다.

#### Response 200

```json
{
  "title": "프로젝트 태스크",
  "description": "업무를 한눈에 확인하고 상세 내용을 확인하세요.",
  "groups": [
    { "group_key": "backlog", "label": "백로그", "count": 0, "tasks": [] },
    {
      "group_key": "todo",
      "label": "투두",
      "count": 2,
      "tasks": [
        {
          "id": "27afd507-9c7f-4f0d-a2be-fcdab2477b19",
          "title": "1차 와이어프레임",
          "role": "frontend",
          "priority": "low",
          "material_count": 2
        }
      ]
    },
    { "group_key": "in_progress", "label": "진행중", "count": 2, "tasks": [] },
    { "group_key": "done", "label": "완료", "count": 2, "tasks": [] },
    { "group_key": "cancelled", "label": "취소", "count": 0, "tasks": [] }
  ]
}
```

보드는 backlog, todo, in_progress, done, cancelled 다섯 그룹을 순서대로 노출합니다. 태스크 수정 화면이 상태 5종을 모두 편집하므로, backlog나 cancelled로 바꾼 태스크가 보드에서 사라지지 않도록 다섯 그룹을 모두 담습니다(MOM-75). 다섯 그룹은 태스크가 없어도 항상 포함하며 그때 tasks는 빈 배열입니다. priority는 low, medium, high로 반환하고, 저장된 값이 레거시 전용인 urgent이면 high로 반환합니다(2026-07-06 가결정). material_count는 관련 자료 연결 데이터가 아직 없어 0으로 고정합니다(합성 필드 정책).

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `PROJECT_NOT_FOUND`
- `AUTH_FORBIDDEN`

### POST /api/mobile/projects/{projectId}/tasks

일반 태스크를 생성합니다.

#### Request

```json
{
  "title": "Write",
  "role": "pm",
  "priority": "medium"
}
```

#### Response 201

```json
{
  "task": {
    "id": "new-task-uuid",
    "project_id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
    "title": "Write",
    "role": "pm",
    "priority": "medium",
    "status": "todo"
  }
}
```

title, role, priority 모두 필수입니다(2026-07-06 기획 확정, 2026-07-07 역할은 하나만 선택하는 단일 값으로 재확정). role은 pm, design, backend, frontend 중 하나입니다(2026-07-08 기획 확정으로 android, qa는 폐기하고 역할은 4종만 둡니다). priority는 low, medium, high 중 하나입니다. 셋 중 하나라도 비거나 role이 4종 밖이면 COMMON_VALIDATION_FAILED로 응답합니다. 생성한 태스크는 todo 그룹에서 시작합니다. role은 레거시 tasks에 없는 신규 속성이라 CHECK 제약을 둔 문자열 컬럼으로 저장합니다.

#### Errors

- `AUTH_UNAUTHORIZED`
- `AUTH_INVALID_TOKEN`
- `PROJECT_NOT_FOUND`
- `COMMON_VALIDATION_FAILED`
- `AUTH_FORBIDDEN`

### GET /api/mobile/tasks/{taskId}

태스크 상세 화면에 필요한 정보를 조회합니다.

#### Response 200

```json
{
  "id": "27afd507-9c7f-4f0d-a2be-fcdab2477b19",
  "project_id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
  "title": "1차 와이어프레임",
  "status": "todo",
  "role": "pm",
  "assignee": {
    "id": "b9b1...e7",
    "name": "김민지"
  },
  "priority": "medium",
  "purpose": "이번 범위에서 확인해야 할 화면 흐름을 정리합니다.",
  "checklist": {
    "completed_count": 2,
    "total_count": 4,
    "items": [
      { "id": "item-1", "title": "어쩌구어쩌구 반영", "completed": true },
      { "id": "item-2", "title": "어쩌구어쩌구 반영", "completed": false }
    ]
  },
  "materials": [
    {
      "id": "m-01",
      "title": "회원가입 에러 메시지 정책 초안",
      "summary": "회원가입의 MVP 완료율과 온보딩 품질에 영향을 줄 수 있습니다.",
      "roles": ["pm"],
      "kind": "doc",
      "source_url": "https://..."
    }
  ],
  "open_questions": [
    {
      "id": "q-01",
      "body": "약한 비밀번호 기준을 사용자에게 얼마나 구체적으로 알려줘야 할지 결정이 필요해보임"
    }
  ],
  "next_action": "민수가 추천해주는 다음행동이에용"
}
```

`materials[].source_url`은 관련자료에서 원본 문서로 이동할 때 사용합니다.

`materials`는 `source_refs`와 `entity_relations`(task ↔ source_ref)로 합성하며, 연결된 자료가 없으면 `[]`,
`material_count`는 `0`입니다. `open_questions`, `next_action`은 MVP에서 backing source가 없으면 각각 `[]`,
`null`로 반환합니다. 서버는 근거 없는 값을 임의 생성하지 않습니다(요구사항 명세 "합성/파생 필드 응답 정책" 참고).

담당자가 지정되지 않았으면 `assignee`는 `null`, 목적을 아직 작성하지 않았으면 `purpose`는 `null`입니다.
완료기준이 없으면 `checklist`는 `completed_count` 0, `total_count` 0, `items` 빈 배열입니다(2026-07-07 확정).
`status`는 저장된 5종(backlog, todo, in_progress, done, cancelled)을 그대로 반환하고(상세 상태 칩이 5종 노출),
`priority`는 보드와 같이 저장된 urgent를 high로 반환합니다. `purpose`는 레거시 `tasks.description`에 매핑됩니다.

#### Errors

- `TASK_NOT_FOUND`
- `AUTH_FORBIDDEN`

### PATCH /api/mobile/tasks/{taskId}

태스크 수정 화면이 저장한 편집 상태 전체를 받아 갱신합니다. title, role, priority, status는 항상 채워 보냅니다. title은 생성과 달리 빈 문자열을 허용하고, title을 빈 문자열로 보내면 상세 화면이 '새 태스크'로 표시합니다.

#### Request

```json
{
  "title": "1차 와이어프레임",
  "role": "pm",
  "assignee_id": "user-uuid",
  "priority": "medium",
  "status": "in_progress",
  "purpose": "이번 범위에서 확인해야 할 화면 흐름을 정리합니다.",
  "checklist_items": [
    { "id": "item-1", "title": "어쩌구어쩌구 반영" },
    { "title": "새 완료기준" }
  ]
}
```

담당자를 비우려면 `assignee_id`를 `null`로 보냅니다. `status`는 backlog, todo, in_progress, done, cancelled 다섯 값 중 하나입니다. `checklist_items`는 완료기준 최종 목록이고, `id`가 있으면 기존 항목을 갱신하고 `id`가 없으면 새로 만들며, 목록에서 빠진 기존 항목은 삭제합니다. 완료기준은 0개에서 5개까지 허용하고, 5개를 넘기면 `COMMON_VALIDATION_FAILED`로 응답합니다. 글자 수 제한은 별도 이슈로 보류되어 여기서 검증하지 않습니다.

#### Response 200

응답의 `task`는 상세 조회와 같은 형식이고 `status`까지 담습니다. 수정을 저장하면 앱이 상세 화면으로 돌아가므로, 다시 조회하지 않고 화면을 갱신할 수 있도록 상세 전체를 반환합니다. `materials`, `open_questions`, `next_action`은 상세와 동일하게 backing source가 생기기 전까지 각각 빈 배열, 빈 배열, null입니다.

```json
{
  "task": {
    "id": "27afd507-9c7f-4f0d-a2be-fcdab2477b19",
    "project_id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
    "title": "1차 와이어프레임",
    "status": "in_progress",
    "role": "pm",
    "assignee": { "id": "user-uuid", "name": "김민지" },
    "priority": "medium",
    "purpose": "이번 범위에서 확인해야 할 화면 흐름을 정리합니다.",
    "checklist": {
      "completed_count": 2,
      "total_count": 4,
      "items": [
        { "id": "item-1", "title": "어쩌구어쩌구 반영", "completed": true },
        { "id": "item-5", "title": "새 완료기준", "completed": false }
      ]
    },
    "materials": [],
    "open_questions": [],
    "next_action": null
  }
}
```

#### Errors

- `TASK_NOT_FOUND`
- `COMMON_VALIDATION_FAILED`
- `AUTH_FORBIDDEN`

### PATCH /api/mobile/tasks/{taskId}/checklist-items/{itemId}

태스크 체크리스트 항목의 완료 상태를 변경합니다.

#### Request

```json
{
  "completed": true
}
```

#### Response 200

```json
{
  "checklist": {
    "completed_count": 2,
    "total_count": 4,
    "item": {
      "id": "item-1",
      "title": "어쩌구어쩌구 반영",
      "completed": true
    }
  }
}
```

#### Errors

- `TASK_NOT_FOUND`
- `TASK_CHECKLIST_ITEM_NOT_FOUND`
- `COMMON_VALIDATION_FAILED`
- `AUTH_FORBIDDEN`
