# Spring Boot JWT Template

A production-ready Spring Boot starter template with JWT authentication, email-based two-phase verification, Redis-backed verification code storage and failed-login tracking, PostgreSQL persistence, host/IP binding, and MaxMind GeoIP geolocation.

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
- [Security Model](#security-model)
  - [Authentication (stateless JWT)](#authentication-stateless-jwt)
  - [Authorization (SecurityFilterChain)](#authorization-securityfilterchain)
  - [Fine-grained access control](#fine-grained-access-control)
  - [Failed login tracking & account lockout](#failed-login-tracking--account-lockout)
  - [Host tracking & IP binding](#host-tracking--ip-binding)
- [Verification Flow](#verification-flow)
- [Getting Started](#getting-started)
  - [1. Clone and Configure](#1-clone-and-configure)
  - [2. Build](#2-build)
  - [3. Run](#3-run)
  - [4. Test](#4-test)
- [API Endpoints](#api-endpoints)
  - [Health](#health)
  - [Authentication](#authentication)
  - [Hosts](#hosts)
  - [GeoIP](#geoip)
  - [Users](#users)
- [Architecture](#architecture)
  - [Project Structure](#project-structure)
  - [Domain Entities](#domain-entities)
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
| Redis | 7+ | Used for verification codes (15 min TTL) and failed-login counters (12 h TTL); Upstash or self-hosted |
| MaxMind GeoLite2-City | — | MMDB file in `src/main/resources/geoip/` or NFS-mounted |

## Configuration

### Environment Variables (.env)

All sensitive configuration lives in `.env` at the project root. This file is **gitignored** and must never be committed.

Spring loads `.env` via `spring.config.import=optional:file:.env[.properties]`, which parses it as a standard Java properties file (key=value, one per line — not shell `export Var=value`).

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

> **How it works:** Spring reads `.env` via `spring.config.import=optional:file:.env[.properties]`. Variable names match Spring property keys directly — no `${...}` placeholders needed in `application.properties`. The file is a standard Java properties file (`key=value`, `#` comments — not shell `export Var=value`).

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

Schema is managed via native PostgreSQL DDL in `src/main/resources/db/migration/`. The schema is applied manually — not via Flyway or any migration tool. Three migration files exist:

| File | What it creates |
|------|-----------------|
| `V1__init.sql` | Enum `user_role` (ADMIN, CUSTOMER), enum `user_status` (ACTIVE, INACTIVE, LOCKED), table `"user"` |
| `V2__host.sql` | Enum `host_status` (ACTIVE, INACTIVE, BANNED), table `host` |
| `V3__event_log.sql` | Table `event_log` |

The DDL in `V1__init.sql` creates:
- **Enum** `jwt_template_app.user_role`: `ADMIN`, `CUSTOMER`
- **Enum** `jwt_template_app.user_status`: `ACTIVE`, `INACTIVE`, `LOCKED`
- **Table** `jwt_template_app."user"`: `id` (UUID PK), `username`, `password`, `first_name`, `last_name`, `email`, `verified`, `role`, `status`, `created_at`, `updated_at`

Password hashing uses **Argon2id** via Spring Security's `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.

### Redis

Redis serves two purposes:

**Verification code storage** (`VerificationCodeStore`):
- Stores verification tokens with a 15-minute TTL
- Key pattern: `verification:{token}` → email address
- Implemented using `StringRedisTemplate`
- Compatible with Upstash (serverless) and any RESP-compatible self-hosted Redis instance

**Failed-login tracking** (`FailedLoginTracker`):
- Counts failed login attempts per user ID
- Key pattern: `failed_logins:{userId}`, TTL 12 hours
- After 5 failed attempts the account is locked (`UserStatus.LOCKED`)
- Configure via `spring.data.redis.url` in `.env`

### Email

Email is sent asynchronously via Spring's `@Async` with a dedicated `ThreadPoolTaskExecutor` (`mailExecutor`, configured in `AsyncConfig`). The executor has:
- Core pool size: 5
- Max pool size: 20
- Queue capacity: 100

Templates are Thymeleaf HTML files in `src/main/resources/templates/mail/`:
- `verification.html` — registration verification email
- `login-verification.html` — login verification email

Email variables injected into templates include: `verificationUrl`, `firstName`, `lastName`, `username`, `email`, `clientIp`, `userAgent`, `time`, `city`, `country`, `countryCode`, `timezone`, `latitude`, `longitude`.

`MailSendException` caught by `GlobalExceptionHandler` — returns generic message, logs detail.

### GeoIP

MaxMind GeoIP2 (version 4.2.1) provides IP-to-location resolution. The `DatabaseReader` is a Spring bean in `GeoIpConfig`, instantiated from the MMDB file specified by `geoip.database-path`.

Client IP extraction: when `geoip.trust-x-forwarded-for=true`, the first IP in the `X-Forwarded-For` header is used; otherwise `HttpServletRequest.getRemoteAddr()` is used.

## Security Model

### Authentication (stateless JWT)

1. `JwtTokenProvider` creates tokens with subject (userId), role, and `ip_address` claims, signed with HMAC-SHA using a BASE64-decoded secret (`app.jwt.secret`). Token expiration is configurable via `app.jwt.expiration-ms` (default: 24 hours).

2. `JwtAuthenticationFilter` (a `OncePerRequestFilter`) intercepts every request before `UsernamePasswordAuthenticationFilter`. It extracts the Bearer token from the `Authorization` header, validates it, and sets the `SecurityContext` with a `UsernamePasswordAuthenticationToken` containing the userId, `ROLE_ADMIN`/`ROLE_CUSTOMER` authority, and the IP address as authentication details.

3. No `UserDetailsService` is used — authentication is purely claim-based. Invalid/expired tokens are silently ignored (the filter chain continues without authentication).

### Authorization (SecurityFilterChain)

- `/auth/**`, `/syn`, `/geoip/**` → `permitAll()`
- `/users/**`, `/hosts/**` → `authenticated()`
- Unauthenticated requests to protected endpoints → 401 `Authentication required.`
- Authenticated but unauthorized → 403 `Insufficient privileges.`

### Fine-grained access control (`ABACRulesService`)

Injected into services, called before operations. Rules:
- **Self-access**: any authenticated user can access their own resource (requesterId == targetId)
- **ADMIN → CUSTOMER**: admin can access customer resources
- **ADMIN → ADMIN**: denied (admins cannot access other admins' resources)
- **CUSTOMER → anything else**: denied
- **IP binding**: when the JWT carries an `ip_address` claim, the current request IP (`X-Forwarded-For` or `getRemoteAddr()`) must match. Mismatch → 403 `Session IP does not match current request`

### Failed login tracking & account lockout

`FailedLoginTracker` (Redis-backed) counts failed login attempts per user ID:
- Key pattern: `failed_logins:{userId}`, TTL 12 hours
- After **5 failed attempts** the account status is set to `LOCKED` (`UserStatus.LOCKED`)
- Locked accounts cannot log in → 403 `Account locked`
- A TODO remains to send a notification email when an account is locked (see `AuthService.java:127`)

### Host tracking & IP binding

`Host` records track IP addresses seen during authentication:
- Table `host`: `id` (UUID PK), `user_id` (FK → user), `ip_address` (unique), `user_agent`, `status` (ACTIVE/INACTIVE/BANNED), `created_at`, `updated_at`
- On each login/verification attempt, the host is looked up by IP + user ID, created if missing, and saved
- If host status is `BANNED` → 403 `Host {ip} is banned from accessing this account`
- Every authentication event is logged to `event_log` (description + timestamp)
- Host status is marked as "unreliable" in `AuthService` — the status field is not trusted for access decisions; only the `BANNED` check is enforced

## Verification Flow

Two-phase verification is required before an account becomes active:

**Registration flow:**
1. `POST /auth/register` → validates input, saves user with `verified=false`, generates UUID token, stores in Redis (15 min TTL), sends verification email with link
2. User clicks link → `GET /auth/verification/{token}` → validates token, checks host not banned, sets `verified=true`, returns JWT token + user

**Login flow:**
1. `POST /auth/login` → validates credentials (username or email + password), checks host not banned, checks `verified=true`, checks account not locked, generates UUID token, stores in Redis (15 min TTL), sends verification email with link
2. User clicks link → `GET /auth/verification/{token}` → validates token, checks host not banned, returns JWT token + user (does not re-set verified since already true)

**Resend flow:**
1. `POST /auth/resend-link?email=...` → validates email, finds unverified user, generates new UUID token, stores in Redis (15 min TTL), sends verification email

**Unlock flow (account locked):**
1. `POST /auth/unlock` (JWT-authenticated) → accepts email of locked account, checks caller has privileges, generates UUID token, stores in Redis (15 min TTL), sends unlock email
2. User clicks link → `GET /auth/verification/{token}` → validates token, unlocks account, returns JWT token

**Change password flow:**
1. `POST /auth/change-password` (JWT-authenticated) → validates current credentials + new password, generates UUID token, stores in Redis (15 min TTL), sends confirmation email
2. User clicks link → `GET /auth/verification/{token}` → validates token, updates password, returns JWT token

**Change email flow:**
1. `POST /auth/change-email` (JWT-authenticated) → validates current credentials + new email, generates UUID token, stores in Redis (15 min TTL), sends confirmation email to new address
2. User clicks link → `GET /auth/verification/{token}` → validates token, updates email, returns JWT token

Redis key pattern: `verification:{token}` → email address, TTL 15 minutes.

> **Note:** `POST /auth/change-password`, `POST /auth/change-email`, and `POST /auth/unlock` are defined in the OpenAPI spec (`docs/api/api.yaml`) but their DTOs and controller methods are not yet implemented. The verification endpoint (`GET /auth/verification/{token}`) handles all token-consuming flows generically.

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
| GET | `/auth/verification/{token}` | — | Verify token → 200 with JWT token + user. Consumes tokens from register, login, resend, change-password, change-email, and unlock flows |
| POST | `/auth/login` | — | Login → 202, sends verification code by email |
| POST | `/auth/resend-link?email=...` | — | Resend verification code → 202 |
| POST | `/auth/change-password` | JWT | Request password change → 202, sends confirmation link by email *(spec'd, not yet implemented)* |
| POST | `/auth/change-email` | JWT | Request email change → 202, sends confirmation link by email *(spec'd, not yet implemented)* |
| POST | `/auth/unlock` | JWT | Request to unlock a locked account → 202, sends unlock link by email *(spec'd, not yet implemented)* |

### Hosts

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/users/{userId}/hosts` | JWT | List hosts (paginated). Non-admin callers see only their own hosts. Filter by ipAddress, status |
| GET | `/users/{userId}/hosts/{hostId}` | JWT | Get a host by ID (includes GeoIP data for its IP). Non-admin callers can only access their own hosts |
| PATCH | `/users/{userId}/hosts/{hostId}` | JWT | Ban a host → sets status to BANNED. Only status transition exposed via API |

### GeoIP

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/geoip` | — | Resolve client geolocation via X-Forwarded-For |
| GET | `/geoip/{ip}` | — | Resolve IP geolocation (IPv4/IPv6) |

### Users

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/users` | JWT | List users (paginated, admin only). Filter by username, firstName, lastName, email |
| GET | `/users/{userId}` | JWT | Get user by ID |
| PATCH | `/users/{userId}` | JWT | Update user (username, firstName, lastName) |
| DELETE | `/users/{userId}` | JWT | Delete user |

## Architecture

### Project Structure

```
com.techindna.springbootjwttemplate
├── SpringBootJwtTemplateApplication.java   # entry point
├── config/
│   ├── AsyncConfig.java                    # @EnableAsync + mailExecutor ThreadPoolTaskExecutor (core:5, max:20, queue:100)
│   └── GeoIpConfig.java                    # DatabaseReader bean (MaxMind), reads from classpath or file:
│                                           #   - classpath:geoip/GeoLite2-City.mmdb (bundled)
│                                           #   - file:/mnt/geoip/GeoLite2-City.mmdb (NFS-mounted)
├── security/
│   ├── SecurityConfig.java                 # SecurityFilterChain, PasswordEncoder (Argon2id)
│   ├── ResourcesAccessRules.java           # authorization: self-access, ADMIN→CUSTOMER; ADMIN→ADMIN denied; IP binding
│   └── jwt/
│       ├── JwtAuthenticationFilter.java    # JWT filter (extracts userId + role + ip from claims, no UserDetailsService)
│       └── JwtTokenProvider.java           # JWT create/parse (HMAC-SHA, BASE64 secret, configurable TTL)
├── controller/
│   ├── AuthController.java                 # POST /auth/register, POST /auth/login, GET /auth/verification/{token}, POST /auth/resend-link
│   ├── GeoIpController.java                # GET /geoip, GET /geoip/{ip}
│   ├── SynController.java                  # GET /syn
│   └── UserController.java                 # GET/PATCH/DELETE /users/{userId}
├── dto/
│   ├── LoginInput.java                     # login request body (username, email, password)
│   ├── MessageBody.java                    # { message } response
│   ├── RegisterInput.java                  # register request body
│   ├── UpdateUserInput.java                # user update request body
│   └── VerifyRegistrationResponse.java     # token + user response
├── entity/
│   ├── GeoIpResponse.java                  # GeoIP lookup result record
│   ├── User.java                           # domain record (read model)
│   ├── Host.java                           # host domain record (read model)
│   ├── EventLog.java                       # event log domain record (read model)
│   ├── email/EmailDetails.java             # email details entity
│   ├── enums/
│   │   ├── UserRole.java                   # user role enum (ADMIN, CUSTOMER)
│   │   ├── UserStatus.java                 # account status enum (ACTIVE, INACTIVE, LOCKED)
│   │   └── HostStatus.java                 # host status enum (ACTIVE, INACTIVE, BANNED)
│   └── enums/                              # enum packages
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
│   ├── HostRepository.java                 # JPA repository (findByIpAddress, findByIpAddressAndUser_Id)
│   ├── LogRepository.java                  # JPA repository (event log CRUD)
│   └── model/
│       ├── JUser.java                      # JPA entity (PostgreSQL "user" table)
│       ├── JHost.java                      # JPA entity (PostgreSQL "host" table)
│       └── JEventLog.java                  # JPA entity (PostgreSQL "event_log" table)
├── service/
│   ├── AuthService.java                    # register + login + verification + resend + host/event logging
│   ├── UserService.java                    # getUser + updateUser (with ResourcesAccessRules)
│   ├── GeoIpService.java                   # IP lookup, client IP extraction
│   ├── VerificationCodeStore.java          # Redis-based verification code storage (15 min TTL, key prefix "verification:")
│   ├── FailedLoginTracker.java             # Redis-based failed login counter (12 h TTL, key prefix "failed_logins:")
│   └── mail/
│       ├── EmailService.java               # email service interface
│       └── EmailSenderService.java         # email service implementation (Thymeleaf + @Async("mailExecutor"))
└── validator/
    ├── DataValidator.java                  # low-level format checks (email, name, username, password, IP)
    └── UserValidator.java                  # registration + login + update rules
```

```
src/main/resources/
├── application.properties
├── db/migration/
│   ├── V1__init.sql                        # native DDL: user_role enum, user_status enum, "user" table
│   ├── V2__host.sql                        # host_status enum, "host" table
│   └── V3__event_log.sql                  # "event_log" table
├── geoip/
│   └── GeoLite2-City.mmdb                  # MaxMind GeoIP database
└── templates/
    └── mail/
        ├── verification.html               # registration email template
        └── login-verification.html         # login email template
```

### Domain Entities

| Table | Purpose | Key columns |
|-------|---------|-------------|
| `users` | User accounts with JWT auth | `id` (UUID PK), `username`, `email`, `password`, `role`, `status`, `verified` |
| `host` | IP address tracking per user | `id` (UUID PK), `user_id` (FK), `ip_address` (unique), `user_agent`, `status` |
| `event_log` | Authentication event audit trail | `id` (UUID PK), `host_id` (FK), `description`, `created_at` |

**Enums:**
- `user_role`: `ADMIN`, `CUSTOMER`
- `user_status`: `ACTIVE`, `INACTIVE`, `LOCKED`
- `host_status`: `ACTIVE`, `INACTIVE`, `BANNED`

**Schema**: native PostgreSQL DDL (`V1__init.sql`, `V2__host.sql`, `V3__event_log.sql`), schema `jwt_template_app` (set via `.env`). Applied manually, not via Flyway. Password hashing: Argon2id (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).

### Conventions

- **IDs**: all UUIDs (`gen_random_uuid()`, `java.util.UUID`)
- **Package**: `com.techindna.springbootjwttemplate`
- **Layer naming**: J-prefix for JPA entities (`JUser`, `JHost`, `JEventLog`), domain records in `entity/`, Lombok `@Getter @Setter @NoArgsConstructor`
- **Validation**: `DataValidator` pattern (void return, throws `UnprocessableContentException` (422)), not `@Valid`
- **Error handling**: custom exceptions → `GlobalExceptionHandler` → JSON `ErrorBody` (status, error, message, timestamp)
- **Mail exceptions**: `MailSendException` (Spring) — handler returns generic message, logs detail
- **JWT auth**: claim-based — extract `userId` + `role` + `ip_address` from token, no `UserDetailsService`
- **Async**: `@EnableAsync` + `@Async("poolName")` on service methods, dedicated `ThreadPoolTaskExecutor` per domain in `AsyncConfig`
- **Resources access**: `ABACRulesService` — inject, call `grantAccessFor()` before operations. Self-access, ADMIN→CUSTOMER; ADMIN→ADMIN denied; IP binding enforced
- **OpenAPI pagination**: `{data: [...], meta: {page (1-indexed), size, total}}`
- **API prefix**: no global prefix — each controller sets its own (`/auth`, `/users`, `/syn`, `/geoip`)
- **GeoIP**: MaxMind MMDB — either `classpath:geoip/GeoLite2-City.mmdb` (bundled in `src/main/resources/geoip/`) or `file:/mnt/geoip/GeoLite2-City.mmdb` (NFS-mounted from a VPS). Configured via `geoip.database-path` in `.env`/`application.properties`. Update the file manually — re-download from MaxMind and replace it (and for NFS, re-export on the VPS).
- **Docs language**: English for API descriptions, French for user-facing instructions
- **Commits**: one commit per logical change, conventional format
- **Code style**: English-only, no comments/docstrings, short focused functions, explicit constructors over `@AllArgsConstructor`
