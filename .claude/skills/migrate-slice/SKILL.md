---
name: migrate-slice
description: Use when planning a vertical migration slice from legacy Go momens-api to Spring momens-server, tracing handlers, services, repositories, schema, tests, and API contracts before implementation.
---

# migrate-slice

Use this project-local skill before implementing a Go -> Spring migration slice.

The goal is to propose the smallest safe vertical slice. Do not implement until the
slice boundary is clear.

## Required Context

Read first:

- `AGENTS.md`
- `docs/rules/architecture.md`
- `docs/rules/code-conventions.md`
- `docs/rules/persistence.md`
- `docs/spec/api-response-error-codes.md`

If product terminology or behavior is unclear, inspect the `teams` repository rather
than guessing.

## Legacy Trace

From the repository root, inspect the legacy Go API in sibling `../momens-api` or
`/Users/kimgyuill/dev/projects/momens/momens-api`.

Trace the feature through:

1. route registration
2. handler request/response shape
3. service rules and errors
4. repository queries
5. domain model fields
6. migrations/schema
7. integration/unit tests

Useful search patterns:

```bash
rg -n "Route|Group|GET|POST|PATCH|DELETE|<feature>" ../momens-api
rg -n "type .*Response|json:\"|Err|error" ../momens-api/internal
rg -n "<table>|<column>|CREATE TABLE|ALTER TABLE" ../momens-api/migrations
```

## Slice Plan

Produce a plan with:

- endpoint(s) included
- endpoint(s) explicitly excluded
- legacy response compatibility requirements
- Spring module/package target
- entity/repository/service/controller work
- migration/Flyway implications
- tests required
- unresolved decisions or assumptions

Prefer one end-to-end path over broad partial migration.

## Guardrails

- Preserve legacy API status/body shape unless the team explicitly changes the contract.
- Do not introduce new dependencies unless docs already allow them or the team confirms.
- Do not decide unsettled module boundaries silently.
- Do not migrate unrelated endpoints just because they share a file.
- Keep the first slice small enough for one focused PR.
