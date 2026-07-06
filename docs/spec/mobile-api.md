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
| `status` | `needs_action`, `completed` |
| `filter` | `needs_action`, `completed` |
| `primary_action` | `convert-to-task` |

### Task

| 이름 | 값 |
| --- | --- |
| `group_key` | `todo`, `in_progress`, `done` |
| `priority` | `high`, `medium`, `low` |
| `roles[]` | `pm`, `design`, `backend`, `frontend`, `android`, `qa` |

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

#### Query

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `filter` | 아니오 | `needs_action`, `completed`. 기본값은 `needs_action` |

#### Response 200

```json
{
  "title": "오늘 확인해야 할 시그널",
  "description": "프로젝트의 의사결정에 영향을 줄 수 있는 변화입니다.",
  "selected_filter": "needs_action",
  "filters": [
    { "key": "needs_action", "label": "확인 필요", "count": 4 },
    { "key": "completed", "label": "확인 완료", "count": 2 }
  ],
  "signals": [
    {
      "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
      "project_id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
      "type": "risk",
      "status": "needs_action",
      "title": "Android 13+ 권한 요청 플로우에서 이탈 가능성 발견",
      "impact": "MVP 완료율과 온보딩 품질에 영향을 줄 수 있습니다.",
      "minsu_suggestion": "내용이 들어갈 공간입니다"
    }
  ]
}
```

#### Errors

- `PROJECT_NOT_FOUND`
- `AUTH_FORBIDDEN`

### GET /api/mobile/signals/{signalId}

시그널 상세 bottom sheet에 필요한 정보를 조회합니다.

#### Response 200

```json
{
  "id": "6f3d8a61-4de7-4c01-9d2b-16fdf182e9a1",
  "project": {
    "id": "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
    "name": "Q2 Activation Readiness"
  },
  "type": "risk",
  "status": "needs_action",
  "status_label": "확인 필요",
  "title": "Android 13+ 권한 요청 플로우에서 이탈 가능성 발견",
  "impact": "MVP 완료율과 온보딩 품질에 영향을 줄 수 있습니다.",
  "evidence": [
    {
      "id": "evidence-uuid",
      "source": "figma",
      "source_title": "권한 요청 화면 v2",
      "occurred_at": "2026-06-28T09:48:00+09:00",
      "relative_time_label": "00분 전",
      "summary": "설명 문구 변경으로 이탈 가능성이 있습니다.",
      "fields": [
        { "label": "text", "value": "text" }
      ],
      "source_url": "https://..."
    }
  ],
  "minsu": {
    "suggestion": "내용이 들어갈 공간입니다",
    "task_draft": {
      "title": "Android 13+ 권한 요청 플로우 점검",
      "roles": ["android"],
      "priority": "medium"
    }
  },
  "primary_action": "convert-to-task"
}
```

#### Errors

- `SIGNAL_NOT_FOUND`
- `AUTH_FORBIDDEN`

### POST /api/mobile/signals/{signalId}/actions/convert-to-task

시그널을 태스크로 등록합니다.

#### Request

요청 body는 선택입니다. body를 보내지 않으면 서버가 시그널의 태스크 초안을 사용합니다.

```json
{
  "title": "Android 13+ 권한 요청 플로우 점검",
  "roles": ["android"],
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
    "status": "completed"
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
    "status": "completed"
  }
}
```

#### Errors

- `SIGNAL_NOT_FOUND`
- `SIGNAL_INVALID_STATE`
- `COMMON_VALIDATION_FAILED`
- `AUTH_FORBIDDEN`

`SIGNAL_INVALID_STATE`(409)는 처리 이력이 없는데 현재 상태에서 전환할 수 없는 경우에만 반환합니다.
이미 전환된 신호의 재요청은 위 `200` 멱등 응답으로 처리합니다.

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
    {
      "group_key": "todo",
      "label": "투두",
      "count": 2,
      "tasks": [
        {
          "id": "27afd507-9c7f-4f0d-a2be-fcdab2477b19",
          "title": "1차 와이어프레임",
          "roles": ["android"],
          "priority": "low",
          "material_count": 2
        }
      ]
    },
    { "group_key": "in_progress", "label": "진행중", "count": 2, "tasks": [] },
    { "group_key": "done", "label": "완료", "count": 2, "tasks": [] }
  ]
}
```

보드는 todo, in_progress, done 세 그룹만 노출하고, 레거시 상태인 backlog와 cancelled는 담지 않습니다. 세 그룹은 태스크가 없어도 항상 포함하며 그때 tasks는 빈 배열입니다. priority는 low, medium, high로 내려가고, 저장된 값이 레거시 전용인 urgent이면 high로 표기합니다(2026-07-06 가결정). material_count는 관련자료 연결 데이터가 아직 없어 0으로 고정합니다(합성 필드 정책).

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
  "roles": ["pm"],
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
    "roles": ["pm"],
    "priority": "medium",
    "status": "todo"
  }
}
```

title, roles, priority 모두 필수입니다(2026-07-06 기획 확정). roles는 하나 이상 선택해야 하고 각 값은 pm, design, backend, frontend, android, qa 중 하나입니다. priority는 low, medium, high 중 하나입니다. 셋 중 하나라도 비면 COMMON_VALIDATION_FAILED로 응답합니다. 생성한 태스크는 todo 그룹에서 시작합니다. roles는 레거시 tasks에 없는 신규 속성이라 별도 테이블 task_roles에 저장합니다.

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
  "roles": ["pm", "android"],
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

#### Errors

- `TASK_NOT_FOUND`
- `AUTH_FORBIDDEN`

### PATCH /api/mobile/tasks/{taskId}

태스크 수정 화면에서 변경한 값을 저장합니다. 보내지 않은 필드는 변경하지 않습니다.

#### Request

```json
{
  "title": "1차 와이어프레임",
  "roles": ["pm", "android"],
  "assignee_id": "user-uuid",
  "priority": "medium",
  "purpose": "이번 범위에서 확인해야 할 화면 흐름을 정리합니다.",
  "checklist_items": [
    { "id": "item-1", "title": "어쩌구어쩌구 반영" },
    { "title": "새 완료기준" }
  ]
}
```

담당자를 비우는 경우 `assignee_id`를 `null`로 보냅니다. `checklist_items`는 완료기준 수정 화면에서 저장할 때 사용하며, `id`가 없는 항목은 새로 생성합니다.

#### Response 200

```json
{
  "task": {
    "id": "27afd507-9c7f-4f0d-a2be-fcdab2477b19",
    "title": "1차 와이어프레임",
    "roles": ["pm", "android"],
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
    }
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
