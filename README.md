# Spring Boot JWT Template

A production-ready Spring Boot starter template with JWT authentication, email verification, Redis-based verification code storage, and PostgreSQL persistence.

## Table of Contents

- [Stack](#stack)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
  - [Environment Variables (.env)](#environment-variables-env)
  - [Mail Configuration](#mail-configuration)
- [Getting Started](#getting-started)
  - [1. Clone and Configure](#1-clone-and-configure)
  - [2. Build](#2-build)
  - [3. Run](#3-run)
  - [4. Test](#4-test)
- [Database](#database)
  - [Schema Management](#schema-management)
  - [Adding a Migration](#adding-a-migration)
- [API Endpoints](#api-endpoints)
  - [Health](#health)
  - [Authentication](#authentication)
  - [Users](#users)
- [Architecture](#architecture)
  - [Project Structure](#project-structure)
  - [Conventions](#conventions)

---

## Stack

Java 21 · Spring Boot 4.1.0 · Spring Data JPA · Spring Security · Spring WebMVC · Spring Mail · Thymeleaf · Redis · PostgreSQL · jjwt 0.12.6 · Lombok · Gradle 9.5.1 · OpenAPI 3.0.3

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | System default may be JDK 26 — always use the toolchain |
| Gradle | 9.5.1+ | Bundled via `./gradlew` |
| PostgreSQL | 14+ | Local or Neon/Supabase |
| Redis | 7+ | Used for verification code storage (15 min TTL) |

## Configuration

### Environment Variables (.env)

All sensitive configuration lives in `.env` at the project root. This file is **gitignored** and must never be committed.

Create a `.env` file with the following variables:

```bash
# Database (PostgreSQL / Neon)
spring.datasource.url=
spring.datasource.username=
spring.datasource.password=
spring.jpa.properties.hibernate.default_schema=

# Mail (Gmail SMTP)
spring.mail.username=
spring.mail.password=

# JWT
app.jwt.secret=

# Redis (Upstash — RESP-compatible)
spring.data.redis.url=
```

> **How it works:** Spring reads `.env` via `spring.config.import=optional:file:.env[.properties]`. Variable names match Spring property keys directly — no `${...}` placeholders needed in `application.properties`.

### Mail Configuration

The application uses Gmail SMTP for sending verification codes. To configure:

1. Enable 2-Step Verification on your Google account
2. Generate an [App Password](https://myaccount.google.com/apppasswords)
3. Set `spring.mail.username` and `spring.mail.password` in `.env`

Host (`smtp.gmail.com`) and port (`587`) are configured in `application.properties`.

## Getting Started

### 1. Clone and Configure

```bash
git clone https://github.com/your-org/spring-boot-jwt-template.git
cd spring-boot-jwt-template
# Create .env with the variables listed below
```

### 2. Build

```bash
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew build
```

> `JAVA_HOME` must point to JDK 21. The system default (JDK 26) is rejected by Gradle 9.5.1.

### 3. Run

```bash
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew bootRun
```

The application starts on `http://localhost:8080`.

### 4. Test

```bash
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew test
```

## Database

### Schema Management

Schema is managed via native PostgreSQL DDL in `src/main/resources/db/migration/V1__init.sql`. The schema is applied manually — not via Flyway or any migration tool.

The DDL creates:
- **Enum** `jwt_template_app.user_role`: `ADMIN`, `CUSTOMER`
- **Table** `jwt_template_app."user"`: `id` (UUID PK), `username`, `email`, `password`, `first_name`, `last_name`, `verified`, `role`, `created_at`, `updated_at`

### Adding a Migration

```bash
# Create a new SQL file for schema changes
touch src/main/resources/db/migration/V2__descriptive_name.sql
```

**Rules:**
- Never edit an already-applied migration — create a new file
- File naming: `V{version}__{description}.sql` (two underscores before description)
- All objects are created in the schema defined by `spring.jpa.properties.hibernate.default_schema` (set via `.env`)

## API Endpoints

Base URL: `http://localhost:8080`

Full OpenAPI spec: [`docs/api/api.yaml`](docs/api/api.yaml)

### Health

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/syn` | — | Health check → 200 with `syn-ack` |

### Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | — | Register → 202, sends verification code by email |
| GET | `/auth/verification?code=...` | — | Verify code → 200 with JWT token |
| POST | `/auth/login` | — | Login → 202, sends verification code by email |

### Users

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/users` | JWT | List users (paginated, admin only) |
| GET | `/users/{userId}` | JWT | Get user by ID |
| PUT | `/users/{userId}` | JWT | Update user |
| DELETE | `/users/{userId}` | JWT | Delete user |

## Architecture

### Project Structure

```
src/main/java/com/techindna/springbootjwttemplate/
├── SpringBootJwtTemplateApplication.java   # entry point
├── config/
│   ├── AsyncConfig.java                    # @EnableAsync + mailExecutor ThreadPoolTaskExecutor
│   ├── JwtAuthenticationFilter.java        # JWT filter
│   ├── JwtTokenProvider.java               # JWT create/parse
│   └── SecurityConfig.java                 # SecurityFilterChain, PasswordEncoder
├── controller/
│   ├── AuthController.java                 # POST /auth/register
│   └── SynController.java                  # GET /syn
├── dto/
│   ├── MessageBody.java                    # { message } response
│   └── RegisterInput.java                  # register request body
├── entity/
│   ├── User.java                           # domain record
│   ├── email/EmailDetails.java             # email details entity
│   └── enums/UserRole.java                 # user role enum
├── exception/
│   ├── ErrorBody.java                      # error response DTO
│   ├── GlobalExceptionHandler.java         # centralized error handling
│   └── http/                               # HTTP exception classes
│       ├── BadRequestException.java        # 400
│       ├── ConflictException.java          # 409
│       ├── ForbiddenException.java         # 403
│       ├── NotFoundException.java          # 404
│       ├── UnauthorizedException.java      # 401
│       └── UnprocessableContentException.java  # 422
├── mapper/
│   └── AuthMapper.java                     # RegisterInput → JUser
├── repository/
│   ├── AuthRepository.java                 # JPA repository
│   └── model/
│       └── JUser.java                      # JPA entity
├── service/
│   ├── AuthService.java                    # register orchestration
│   ├── VerificationCodeStore.java          # Redis-based verification code storage
│   └── mail/
│       ├── EmailService.java               # email service interface
│       └── EmailSenderService.java         # email service implementation
└── validator/
    ├── DataValidator.java                  # low-level format checks
    └── UserValidator.java                  # registration rules

src/main/resources/
├── application.properties
├── db/migration/
│   └── V1__init.sql                        # native DDL: enum + user table
└── templates/
    └── mail/
        └── verification.html               # Thymeleaf verification email template
```

### Conventions

- **IDs**: UUIDs everywhere (`gen_random_uuid()`, `java.util.UUID`)
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
