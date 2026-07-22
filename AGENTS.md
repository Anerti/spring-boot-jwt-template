# spring-boot-jwt-template

## Stack

Java 21 · Spring Boot 4.1.0 · Spring Data JPA · Spring WebMVC · PostgreSQL · Lombok · Gradle 9.5.1 · OpenAPI 3.0.3

## Project structure

```
com.techindna.springbootjwttemplate
├── SpringBootJwtTemplateApplication.java   # entry point
└── (planned: controller, service, repository, entity, dto, config, exception packages)

docs/
├── api/api.yaml          # OpenAPI 3.0.3 spec (source of truth for endpoints)
└── mcd.canvas            # Obsidian data model canvas

src/main/resources/
├── application.properties
└── db/schema.sql         # PostgreSQL schema (users table)
```

## Domain entities

| Table    | Purpose                                   | Key columns                                                     |
|----------|-------------------------------------------|-----------------------------------------------------------------|
| `users`  | User accounts with JWT auth               | `id` (UUID PK), `username`, `email`, `password`, `role`, `verified`, `verification_code` |

**Enum** `user_role`: `admin`, `customer`

**Schema managed by**: `schema.sql` (no Hibernate auto-DDL, no Flyway)

## OpenAPI spec endpoints

| Method | Path                | Auth | Description                                      |
|--------|---------------------|------|--------------------------------------------------|
| POST   | /auth/register      | —    | Register → 202, sends verification code by email |
| GET    | /auth/verification  | —    | Verify code → 200 with JWT token                 |
| POST   | /auth/login         | —    | Login → 202, sends verification code by email    |
| GET    | /users              | JWT  | List users (paginated, admin only)               |
| GET    | /users/{userId}     | JWT  | Get user by ID                                   |
| PUT    | /users/{userId}     | JWT  | Update user                                      |
| DELETE | /users/{userId}     | JWT  | Delete user                                      |

## Common commands

```bash
# build (JDK 21 toolchain required)
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew build

# test
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew test

# run
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew bootRun

# format
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew spotlessApply
```

> `JAVA_HOME` must point to JDK 21 — the system default may be newer (JDK 26) and Gradle 9.5.1 rejects it.

## Conventions

- **IDs**: all UUIDs (`gen_random_uuid()`, `java.util.UUID`)
- **Package**: `com.techindna.springbootjwttemplate`
- **Layer naming**: J-prefix for JPA entities (`JUser`), domain records in `entity/`, Lombok `@Getter @Setter @NoArgsConstructor`
- **Validation**: `DataValidator` pattern (void return, throws `UnprocessableEntityException` (422)), not `@Valid`
- **Error handling**: custom exceptions → `GlobalExceptionHandler` → JSON `ErrorBody` (status, error, message, timestamp)
- **JWT auth**: claim-based — extract `userId` + `role` from token, no `UserDetailsService`
- **Async**: `ExecutorService.newVirtualThreadPerTaskExecutor()`, no `@Async`
- **Resources access**: `ResourcesAccessRules` — inject, call `grantAccessFor()` before operations. ADMIN→CUSTOMER; self-only
- **OpenAPI pagination**: `{data: [...], meta: {page (1-indexed), size, total}}`
- **API prefix**: all endpoints under `/api/v1`
- **Docs language**: English for API descriptions, French for user-facing instructions
- **Commits**: one commit per logical change, conventional format
- **Code style**: English-only, no comments/docstrings, short focused functions, explicit constructors over `@AllArgsConstructor`

## Common pitfalls

- **JDK version**: system default is JDK 26 but Gradle 8.5+ rejects it. Always prefix with `JAVA_HOME=$HOME/.jdks/ms-21.0.11`. Never set `org.gradle.java.home` in `gradle.properties` (Gradle rejects it).
- **`.env` is gitignored**: secrets go in `.env`, never committed.
- **`docs/` contains an Obsidian vault**: `.obsidian/` is gitignored.
- **Spec-only phase**: most Java code is not yet written. The OpenAPI spec (`docs/api/api.yaml`) is the source of truth for endpoint design.
- **Schema is manual**: `schema.sql` is the single source — no Flyway, no Hibernate DDL auto-generation.
