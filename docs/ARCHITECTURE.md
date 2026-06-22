# Architecture

`momens-server` is intended to be a modular modulith.

The goal is one deployable Spring Boot application with a Gradle multi-module
project structure and explicit internal module boundaries. This keeps runtime
deployment simple while giving the codebase enough structure to avoid a large
unbounded application package.

## System Context

```text
Client
  -> momens-server
       -> PostgreSQL
       -> momens-retrieval via gRPC

External Sources
  -> momens-worker
       -> PostgreSQL

momens-retrieval
  -> PostgreSQL load and polling
```

`momens-server` replaces the legacy Go/Gin `momens-api` service. It does not
replace `momens-worker` or `momens-retrieval`.

## Internal Module Direction

The boot application module name is confirmed as `app`.

Other application module names are TBD. A possible domain-oriented shape is:

```text
platform
workspace
product
memory
source
retrieval-integration
```

The exact Gradle modules should be decided before implementation starts.

## Dependency Direction

Potential direction:

```text
workspace -> platform
product -> platform
memory -> platform
source -> platform
retrieval-integration -> platform

product -> workspace
memory -> workspace
source -> workspace
retrieval-integration -> workspace
```

Other modules should use `workspace` through permission-facing services instead
of depending on deep workspace internals.

## Architecture Rules

- Keep one deployable application.
- Use Gradle multi-module project structure.
- Verify module boundaries with Spring Modulith tests.
- Keep controllers thin.
- Keep transaction boundaries in service methods.
- Keep database access behind repositories.
- Keep platform concerns in `platform`.
- Do not introduce microservice boundaries during initial setup.
