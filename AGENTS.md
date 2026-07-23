# spring-boot-jwt-template

## Stack

Java 21 · Spring Boot 4.1.0 · Spring Data JPA · Spring WebMVC · PostgreSQL · Redis · Thymeleaf · Lombok · Gradle 9.5.1 · OpenAPI 3.0.3

## Project structure

```
com.techindna.springbootjwttemplate
├── SpringBootJwtTemplateApplication.java   # entry point
├── config/
│   ├── AsyncConfig.java                   # @EnableAsync + mailExecutor ThreadPoolTaskExecutor
│   ├── JwtAuthenticationFilter.java       # JWT filter
│   ├── JwtTokenProvider.java              # JWT create/parse
│   └── SecurityConfig.java               # SecurityFilterChain, PasswordEncoder
├── controller/
│   ├── AuthController.java                # POST /auth/register
│   └── SynController.java                 # GET /syn
├── dto/
│   ├── MessageBody.java                   # { message } response
│   └── RegisterInput.java                 # register request body
├── entity/
│   ├── User.java                          # domain record
│   ├── email/EmailDetails.java            # email details entity
│   └── enums/UserRole.java                # user role enum
├── exception/
│   ├── ErrorBody.java                     # error response DTO
│   ├── GlobalExceptionHandler.java        # centralized error handling
│   └── http/                              # HTTP exception classes
│       ├── BadRequestException.java       # 400
│       ├── ConflictException.java         # 409
│       ├── ForbiddenException.java        # 403
│       ├── NotFoundException.java         # 404
│       ├── UnauthorizedException.java     # 401
│       └── UnprocessableContentException.java  # 422
├── mapper/
│   └── AuthMapper.java                    # RegisterInput → JUser
├── repository/
│   ├── AuthRepository.java                # JPA repository
│   └── model/
│       └── JUser.java                     # JPA entity
├── service/
│   ├── AuthService.java                   # register orchestration
│   ├── VerificationCodeStore.java         # Redis-based verification code storage
│   └── mail/
│       ├── EmailService.java              # email service interface
│       └── EmailSenderService.java        # email service implementation
└── validator/
    ├── DataValidator.java                 # low-level format checks
    └── UserValidator.java                 # registration rules

docs/
├── api/api.yaml          # OpenAPI 3.0.3 spec (source of truth for endpoints)
└── mcd.canvas            # Obsidian data model canvas

src/main/resources/
├── application.properties
├── db/migration/
│   └── V1__init.sql           # native DDL: enum + user table
└── templates/
    └── mail/
        └── verification.html   # Thymeleaf verification email template
```

## Domain entities

| Table    | Purpose                                   | Key columns                                                     |
|----------|-------------------------------------------|-----------------------------------------------------------------|
| `users`  | User accounts with JWT auth               | `id` (UUID PK), `username`, `email`, `password`, `role`, `verified` |

**Enum** `user_role`: `ADMIN`, `CUSTOMER`

**Schema**: native PostgreSQL DDL (`V1__init.sql`), schema `jwt_template_app` (set via `.env`). Applied manually, not via Flyway.

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
- **Validation**: `DataValidator` pattern (void return, throws `UnprocessableContentException` (422)), not `@Valid`
- **Error handling**: custom exceptions → `GlobalExceptionHandler` → JSON `ErrorBody` (status, error, message, timestamp)
- **Mail exceptions**: `MailSendException` (Spring) — handler returns generic message, logs detail
- **JWT auth**: claim-based — extract `userId` + `role` from token, no `UserDetailsService`
- **Async**: `@EnableAsync` + `@Async("poolName")` on service methods, dedicated `ThreadPoolTaskExecutor` per domain in `AsyncConfig`
- **Resources access**: `ResourcesAccessRules` — inject, call `grantAccessFor()` before operations. ADMIN→CUSTOMER; self-only
- **OpenAPI pagination**: `{data: [...], meta: {page (1-indexed), size, total}}`
- **API prefix**: no global prefix — each controller sets its own (`/auth`, `/users`, `/syn`)
- **Docs language**: English for API descriptions, French for user-facing instructions
- **Commits**: one commit per logical change, conventional format
- **Code style**: English-only, no comments/docstrings, short focused functions, explicit constructors over `@AllArgsConstructor`

## Common pitfalls

- **JDK version**: system default is JDK 26 but Gradle 8.5+ rejects it. Always prefix with `JAVA_HOME=$HOME/.jdks/ms-21.0.11`. Never set `org.gradle.java.home` in `gradle.properties` (Gradle rejects it).
- **`.env` is gitignored**: secrets go in `.env`, never committed.
- **`docs/` contains an Obsidian vault**: `.obsidian/` is gitignored.
- **Partial implementation**: POST /auth/register implemented. GET /auth/verification, POST /auth/login, and users CRUD still planned. The OpenAPI spec (`docs/api/api.yaml`) is the source of truth for endpoints.
- **Schema is native DDL**: `db/migration/V1__init.sql` is the source of truth but is applied manually. Never edit it in-place — create a new SQL file for changes.
