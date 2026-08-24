# Spring Boot JWT Template

A production-ready Spring Boot starter template with JWT authentication, email verification, Redis-based verification code storage, PostgreSQL persistence, and MaxMind GeoIP geolocation.

## Table of Contents

- [Stack](#stack)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
  - [Environment Variables (.env)](#environment-variables-env)
  - [Mail Configuration](#mail-configuration)
  - [GeoIP Configuration](#geoip-configuration)
- [Infrastructure](#infrastructure)
  - [Database](#database)
  - [Redis](#redis)
  - [Email](#email)
  - [GeoIP](#geoip)
- [Getting Started](#getting-started)
  - [1. Clone and Configure](#1-clone-and-configure)
  - [2. Build](#2-build)
  - [3. Run](#3-run)
  - [4. Test](#4-test)
- [API Endpoints](#api-endpoints)
  - [Health](#health)
  - [Authentication](#authentication)
  - [GeoIP](#geoip)
  - [Users](#users)
- [Architecture](#architecture)
  - [Project Structure](#project-structure)
  - [Security Architecture](#security-architecture)
  - [Verification Flow](#verification-flow)
  - [Conventions](#conventions)

---

## Stack

Java 26 · Spring Boot 4.1.0 · Spring Data JPA · Spring Security · Spring WebMVC · Spring Mail · Thymeleaf · Redis · PostgreSQL · Argon2id · jjwt 0.12.6 · MaxMind GeoIP2 · Lombok · Gradle 9.5.1 · OpenAPI 3.0.3

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 26 | System default is JDK 26 — always use the toolchain |
| Gradle | 9.5.1+ | Bundled via `./gradlew` |
| PostgreSQL | 14+ | Local or Neon/Supabase; schema `jwt_template_app` |
| Redis | 7+ | Used for verification code storage (15 min TTL); Upstash or self-hosted |
| MaxMind GeoLite2-City | — | MMDB file in `src/main/resources/geoip/` or NFS-mounted |

## Configuration

### Environment Variables (.env)

All sensitive configuration lives in `.env` at the project root. This file is **gitignored** and must never be committed.

Spring loads `.env` via `spring.config.import=optional:file:.env[.properties]`, which parses it as a standard Java properties file (key=value, one per line — not shell `export VAR=value`).

Create a `.env` file with the following variables:

```properties
# Database (PostgreSQL / Neon) — credentials inline in JDBC URL
spring.datasource.url=
spring.jpa.properties.hibernate.default_schema=jwt_template_app

# Mail (Gmail SMTP — requires App Password, not your account password)
spring.mail.username=
spring.mail.password=

# JWT — must be BASE64-encoded (decoded via BASE64.decode() in JwtTokenProvider)
# Generate one with: openssl rand -base64 32
app.jwt.secret=
# Base URL for building verification links (e.g. http://localhost:8080)
app.base-url=

# Redis (Upstash or self-hosted — use rediss:// for SSL/TLS)
spring.data.redis.url=

# GeoIP — bundled or NFS-mounted from VPS
# geoip.database-path=classpath:geoip/GeoLite2-City.mmdb
# geoip.database-path=file:/mnt/geoip/GeoLite2-City.mmdb
```

> **How it works:** Spring reads `.env` via `spring.config.import=optional:file:.env[.properties]`. Variable names match Spring property keys directly — no `${...}` placeholders needed in `application.properties`. The file is a standard Java properties file (`key=value`, `#` comments — not shell `export VAR=value`).

#### JWT secret requirements

`app.jwt.secret` is decoded with `BASE64.decode()` in `JwtTokenProvider`. It must be:
- **BASE64-encoded** (not raw text)
- Long enough to be a secure HMAC key (≥ 256 bits / 32 bytes when decoded)

Generate one:

```bash
openssl rand -base64 32
```

#### Optional overrides

These properties have defaults in `application.properties` and only need to appear in `.env` if you want to change them:

| Property | Default (in application.properties) | When to override |
|----------|-------------------------------------|------------------|
| `geoip.database-path` | `classpath:geoip/GeoLite2-City.mmdb` | When using NFS-mounted MMDB from a VPS (`file:/mnt/geoip/GeoLite2-City.mmdb`) — set explicitly in `.env` |
| `geoip.trust-x-forwarded-for` | `true` | When behind a proxy that should not be trusted |
| `app.jwt.expiration-ms` | `86400000` (24 hours) | To change token lifetime |

#### Gmail SMTP setup

1. Enable 2-Step Verification on your Google account
2. Generate an [App Password](https://myaccount.google.com/apppasswords)
3. Set `spring.mail.username` (your Gmail address) and `spring.mail.password` (the App Password) in `.env`

Host (`smtp.gmail.com`) and port (`587`) are configured in `application.properties` and do not need to be in `.env`.

### Mail Configuration

The application uses Gmail SMTP for sending verification codes. To configure:

1. Enable 2-Step Verification on your Google account
2. Generate an [App Password](https://myaccount.google.com/apppasswords)
3. Set `spring.mail.username` and `spring.mail.password` in `.env`

Host (`smtp.gmail.com`) and port (`587`) are configured in `application.properties`.

### GeoIP Configuration

The application uses MaxMind GeoLite2-City for IP geolocation.

**Option A — bundled:** place `GeoLite2-City.mmdb` at `src/main/resources/geoip/` and set `geoip.database-path=classpath:geoip/GeoLite2-City.mmdb` in `application.properties`.

**Option B — NFS-mounted from a VPS:** mount the MMDB over NFS (read-only) onto the app server (e.g. `/mnt/geoip/GeoLite2-City.mmdb`) and set `geoip.database-path=file:/mnt/geoip/GeoLite2-City.mmdb` via `.env` or `application.properties`.

| Property | Default | Description |
|----------|---------|-------------|
| `geoip.database-path` | `classpath:geoip/GeoLite2-City.mmdb` | Path to the MaxMind MMDB file (`classpath:` or `file:`) |
| `geoip.trust-x-forwarded-for` | `true` | Use `X-Forwarded-For` header for client IP |

To update the database: download a new `GeoLite2-City.mmdb` from [MaxMind](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data) and, for the bundled option, replace the file at `src/main/resources/geoip/GeoLite2-City.mmdb`; for the NFS option, replace the file on the VPS at `/srv/geoip/GeoLite2-City.mmdb` and ensure the export is still active (`sudo exportfs -ra`).

## Infrastructure

### Database

Schema is managed via native PostgreSQL DDL in `src/main/resources/db/migration/V1__init.sql`. The schema is applied manually — not via Flyway or any migration tool.

The DDL creates:
- **Enum** `jwt_template_app.user_role`: `ADMIN`, `CUSTOMER`
- **Table** `jwt_template_app."user"`: `id` (UUID PK), `username`, `password`, `first_name`, `last_name`, `email`, `verified`, `role`, `created_at`, `updated_at`

Password hashing uses **Argon2id** via Spring Security's `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.

### Redis

Redis stores verification tokens with a 15-minute TTL. Each token is a UUID mapped to an email address under the key prefix `verification:`. Implemented in `VerificationCodeStore` using `StringRedisTemplate`.

Compatible with Upstash (serverless Redis) and any RESP-compatible self-hosted Redis instance. Configure via `spring.data.redis.url` in `.env`.

### Email

Email is sent asynchronously via Spring's `@Async` with a dedicated `ThreadPoolTaskExecutor` (`mailExecutor`, configured in `AsyncConfig`). The executor has:
- Core pool size: 5
- Max pool size: 20
- Queue capacity: 100

Templates are Thymeleaf HTML files in `src/main/resources/templates/mail/`:
- `verification.html` — registration verification email
- `login-verification.html` — login verification email

### GeoIP

MaxMind GeoIP2 (version 4.2.1) provides IP-to-location resolution. The `DatabaseReader` is a Spring bean in `GeoIpConfig`, instantiated from the MMDB file specified by `geoip.database-path`.

Client IP extraction: when `geoip.trust-x-forwarded-for=true`, the first IP in the `X-Forwarded-For` header is used; otherwise `HttpServletRequest.getRemoteAddr()` is used.

## Getting Started

### 1. Clone and Configure

```bash
git clone https://github.com/your-org/spring-boot-jwt-template.git
cd spring-boot-jwt-template
# Create .env with the variables listed below
```

### 2. Build

```bash
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew build
```

> `JAVA_HOME` must point to JDK 26 — the system default is JDK 26. Never set `org.gradle.java.home` in `gradle.properties` (Gradle rejects it).

### 3. Run

```bash
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew bootRun
```

The application starts on `http://localhost:8080`.

### 4. Test

```bash
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew test
```

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
| GET | `/auth/verification/{token}` | — | Verify token → 200 with JWT token |
| POST | `/auth/login` | — | Login → 202, sends verification code by email |
| POST | `/auth/resend-link?email=...` | — | Resend verification code → 202 |

### GeoIP

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/geoip` | — | Resolve client geolocation via X-Forwarded-For |
| GET | `/geoip/{ip}` | — | Resolve IP geolocation (IPv4/IPv6) |

### Users

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/users` | JWT | List users (paginated, admin only) |
| GET | `/users/{userId}` | JWT | Get user by ID |
| PATCH | `/users/{userId}` | JWT | Update user |
| DELETE | `/users/{userId}` | JWT | Delete user |

## Architecture

### Project Structure

```
src/main/java/com/techindna/springbootjwttemplate/
├── SpringBootJwtTemplateApplication.java   # entry point
├── config/
│   ├── AsyncConfig.java                    # @EnableAsync + mailExecutor ThreadPoolTaskExecutor
│   └── GeoIpConfig.java                   # DatabaseReader bean (MaxMind)
├── security/
│   ├── SecurityConfig.java                 # SecurityFilterChain, PasswordEncoder (Argon2id)
│   ├── ResourcesAccessRules.java           # authorization: self-access, ADMIN→CUSTOMER; ADMIN→ADMIN denied
│   └── jwt/
│       ├── JwtAuthenticationFilter.java    # JWT filter (extracts userId + role from claims)
│       └── JwtTokenProvider.java           # JWT create/parse (HMAC-SHA, BASE64 secret)
├── controller/
│   ├── AuthController.java                 # POST /auth/register, POST /auth/login, GET /auth/verification, POST /auth/resend-link
│   ├── GeoIpController.java                # GET /geoip, GET /geoip/{ip}
│   ├── SynController.java                  # GET /syn
│   └── UserController.java                 # GET/PATCH/DELETE /users/{userId}
├── dto/
│   ├── LoginInput.java                     # login request body
│   ├── MessageBody.java                    # { message } response
│   ├── RegisterInput.java                  # register request body
│   ├── UpdateUserInput.java                # user update request body
│   └── VerifyRegistrationResponse.java     # token + user response
├── entity/
│   ├── GeoIpResponse.java                  # GeoIP lookup result record
│   ├── User.java                           # domain record (read model)
│   ├── email/EmailDetails.java             # email details entity
│   └── enums/UserRole.java                 # user role enum (ADMIN, CUSTOMER)
├── exception/
│   ├── ErrorBody.java                      # error response DTO (status, error, message, timestamp)
│   ├── GlobalExceptionHandler.java         # centralized error handling (@RestControllerAdvice)
│   └── http/                               # HTTP exception classes
│       ├── BadRequestException.java        # 400
│       ├── ConflictException.java          # 409
│       ├── ForbiddenException.java         # 403
│       ├── GoneException.java              # 410
│       ├── NotFoundException.java          # 404
│       ├── UnauthorizedException.java      # 401
│       └── UnprocessableContentException.java  # 422
├── mapper/
│   ├── UserMapper.java                     # RegisterInput → JUser, JUser → User
│   └── GeoIpMapper.java                    # CityResponse → GeoIpResponse
├── repository/
│   ├── AuthRepository.java                 # JPA repository (findByEmail, findByUsername)
│   ├── UserRepository.java                 # JPA repository (CRUD)
│   └── model/
│       └── JUser.java                      # JPA entity (PostgreSQL "user" table)
├── service/
│   ├── AuthService.java                    # register + login + verification + resend
│   ├── UserService.java                    # getUser + updateUser (with ResourcesAccessRules)
│   ├── GeoIpService.java                   # IP lookup, client IP extraction
│   ├── VerificationCodeStore.java          # Redis-based verification code storage (15 min TTL)
│   └── mail/
│       ├── EmailService.java               # email service interface
│       └── EmailSenderService.java         # email service implementation (Thymeleaf + async)
└── validator/
    ├── DataValidator.java                  # low-level format checks (email, name, username, password, IP)
    └── UserValidator.java                  # registration + login + update rules

src/main/resources/
├── application.properties
├── db/migration/
│   └── V1__init.sql                        # native DDL: enum + user table
├── geoip/
│   └── GeoLite2-City.mmdb                  # MaxMind GeoIP database
└── templates/
    └── mail/
        ├── verification.html               # registration email template
        └── login-verification.html         # login email template
```

### Security Architecture

**Authentication (stateless JWT):**

1. `JwtTokenProvider` creates tokens with subject (userId) and role claims, signed with HMAC-SHA using a BASE64-decoded secret (`app.jwt.secret`). Token expiration is configurable via `app.jwt.expiration-ms` (default: 24 hours).

2. `JwtAuthenticationFilter` (a `OncePerRequestFilter`) intercepts every request before `UsernamePasswordAuthenticationFilter`. It extracts the Bearer token from the `Authorization` header, validates it, and sets the `SecurityContext` with a `UsernamePasswordAuthenticationToken` containing the userId and `ROLE_ADMIN`/`ROLE_CUSTOMER` authority.

3. No `UserDetailsService` is used — authentication is purely claim-based. Invalid/expired tokens are silently ignored (the filter chain continues without authentication).

**Authorization (SecurityFilterChain):**

- `/auth/**`, `/syn`, `/geoip/**` → `permitAll()`
- `/users/**` → `authenticated()`
- Unauthenticated requests to protected endpoints → 401 `Authentication required.`
- Authenticated but unauthorized → 403 `Insufficient privileges.`

**Fine-grained access control (`ResourcesAccessRules`):**

Injected into `UserService`, called before operations. Rules:
- **Self-access**: any authenticated user can access their own resource (requesterId == targetId)
- **ADMIN → CUSTOMER**: admin can access customer resources
- **ADMIN → ADMIN**: denied (admins cannot access other admins' resources)
- **CUSTOMER → anything else**: denied

**Password hashing:** Argon2id via `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.

### Verification Flow

Two-phase verification is required before an account becomes active:

**Registration flow:**
1. `POST /auth/register` → validates input, saves user with `verified=false`, generates UUID token, stores in Redis (15 min TTL), sends verification email with link
2. User clicks link → `GET /auth/verification/{token}` → validates token, sets `verified=true`, returns JWT token

**Login flow:**
1. `POST /auth/login` → validates credentials, checks `verified=true`, generates UUID token, stores in Redis (15 min TTL), sends verification email with link
2. User clicks link → `GET /auth/verification/{token}` → validates token, returns JWT token (does not re-set verified since already true)

**Resend flow:**
1. `POST /auth/resend-link?email=...` → validates email, finds unverified user, generates new UUID token, stores in Redis (15 min TTL), sends verification email

Redis key pattern: `verification:{token}` → email address, TTL 15 minutes.

### Conventions

- **IDs**: all UUIDs (`gen_random_uuid()`, `java.util.UUID`)
- **Package**: `com.techindna.springbootjwttemplate`
- **Layer naming**: J-prefix for JPA entities (`JUser`), domain records in `entity/`, Lombok `@Getter @Setter @NoArgsConstructor`
- **Validation**: `DataValidator` pattern (void return, throws `UnprocessableContentException` (422)), not `@Valid`
- **Error handling**: custom exceptions → `GlobalExceptionHandler` → JSON `ErrorBody` (status, error, message, timestamp)
- **Mail exceptions**: `MailSendException` (Spring) — handler returns generic message, logs detail
- **JWT auth**: claim-based — extract `userId` + `role` from token, no `UserDetailsService`
- **Async**: `@EnableAsync` + `@Async("poolName")` on service methods, dedicated `ThreadPoolTaskExecutor` per domain in `AsyncConfig`
- **Resources access**: `ResourcesAccessRules` — inject, call `grantAccessFor()` before operations. Self-access, ADMIN→CUSTOMER; ADMIN→ADMIN denied
- **OpenAPI pagination**: `{data: [...], meta: {page (1-indexed), size, total}}`
- **API prefix**: no global prefix — each controller sets its own (`/auth`, `/users`, `/syn`, `/geoip`)
- **GeoIP**: MaxMind MMDB — either `classpath:geoip/GeoLite2-City.mmdb` (bundled in `src/main/resources/geoip/`) or `file:/mnt/geoip/GeoLite2-City.mmdb` (NFS-mounted from a VPS). Configured via `geoip.database-path` in `.env`/`application.properties`. Update the file manually — re-download from MaxMind and replace it (and for NFS, re-export on the VPS).
- **Docs language**: English for API descriptions, French for user-facing instructions
- **Commits**: one commit per logical change, conventional format
- **Code style**: English-only, no comments/docstrings, short focused functions, explicit constructors over `@AllArgsConstructor`

