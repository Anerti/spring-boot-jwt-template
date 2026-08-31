# spring-boot-jwt-template

## Stack

Java 26 · Spring Boot 4.1.0 · Spring Data JPA · Spring WebMVC · PostgreSQL · Redis · Thymeleaf · MaxMind GeoIP2 · Lombok · Gradle 9.5.1 · OpenAPI 3.0.3

## Project structure

```
com.techindna.springbootjwttemplate
├── SpringBootJwtTemplateApplication.java   # entry point
├── config/
│   ├── AsyncConfig.java                   # @EnableAsync + mailExecutor ThreadPoolTaskExecutor (core:5, max:20, queue:100)
│   └── GeoIpConfig.java                  # DatabaseReader bean (MaxMind), reads from classpath or file:
│                                         #   - classpath:geoip/GeoLite2-City.mmdb (bundled)
│                                         #   - file:/mnt/geoip/GeoLite2-City.mmdb (NFS-mounted)
├── security/
│   ├── SecurityConfig.java               # SecurityFilterChain, PasswordEncoder (Argon2id)
│   ├── ResourcesAccessRules.java         # authorization: ADMIN→CUSTOMER; ADMIN→ADMIN denied; IP binding
│   └── jwt/
│       ├── JwtAuthenticationFilter.java   # JWT filter (extracts userId + role + ip from claims, no UserDetailsService)
│       └── JwtTokenProvider.java          # JWT create/parse (HMAC-SHA, BASE64 secret, configurable TTL)
├── controller/
│   ├── AuthController.java               # POST /auth/register, POST /auth/login, GET /auth/verification/{token}, POST /auth/resend-link, POST /auth/change-password, POST /auth/change-email, POST /auth/unlock, POST /auth/logout
│   ├── GeoIpController.java              # GET /geoip, GET /geoip/{ip}
│   ├── SynController.java                # GET /syn
│   └── UserController.java               # GET/PATCH/DELETE /users/{userId}
│   └── HostController.java               # GET /users/{userId}/hosts
├── dto/
│   ├── LoginInput.java                   # login request body (username/email, password)
│   ├── RegisterInput.java                # registration request body
│   ├── ChangePasswordInput.java          # change-password request body
│   ├── ChangeEmailInput.java             # change-email request body
│   ├── UnlockAccountInput.java           # unlock request body (email)
│   ├── UpdateUserInput.java              # user update request body
│   ├── MessageBody.java                  # { message } response
│   ├── VerifyRegistrationResponse.java   # token + user response
│   ├── HostListQuery.java                # host list filter + sort params
│   ├── Meta.java                         # pagination metadata
│   └── PaginatedResponse.java            # { data, meta } envelope
├── entity/
│   ├── GeoIpResponse.java               # GeoIP lookup result record
│   ├── User.java                         # domain record (read model)
│   ├── Host.java                         # host domain record (read model)
│   ├── EventLog.java                     # event log domain record (read model)
│   ├── email/EmailDetails.java           # email details entity
│   └── enums/
│       ├── UserRole.java                 # user role enum (ADMIN, CUSTOMER)
│       ├── UserStatus.java               # account status enum (ACTIVE, INACTIVE, LOCKED)
│       └── HostStatus.java               # host status enum (AUTHORIZED, BANNED)
├── exception/
│   ├── ErrorBody.java                    # error response DTO (status, error, message, timestamp)
│   ├── GlobalExceptionHandler.java       # centralized error handling (@RestControllerAdvice)
│   └── http/                             # HTTP exception classes
│       ├── BadRequestException.java       # 400
│       ├── ConflictException.java         # 409
│       ├── ForbiddenException.java        # 403
│       ├── GoneException.java             # 410
│       ├── NotFoundException.java         # 404
│       ├── UnauthorizedException.java     # 401
│       └── UnprocessableContentException.java  # 422
├── mapper/
│   ├── UserMapper.java                   # RegisterInput → JUser, JUser → User
│   ├── GeoIpMapper.java                 # CityResponse → GeoIpResponse
│   └── HostMapper.java                  # JHost → Host
├── repository/
│   ├── AuthRepository.java               # JPA repository (findByEmail, findByUsername)
│   ├── UserRepository.java               # JPA repository (CRUD)
│   ├── HostRepository.java               # JPA repository (findByIpAddress, findByIpAddressAndUser_Id, search with filters)
│   ├── LogRepository.java                # JPA repository (event log CRUD)
│   └── model/
│       ├── JUser.java                    # JPA entity (PostgreSQL "user" table)
│       ├── JHost.java                    # JPA entity (PostgreSQL "host" table)
│       └── JEventLog.java                # JPA entity (PostgreSQL "event_log" table)
├── service/
│   ├── AuthService.java                  # register + login + verification + resend + unlock + change-password/email + failed login tracking
│   ├── UserService.java                  # getUser + updateUser (with ABACRulesService)
│   ├── HostService.java                  # listHosts (paginated, filtered, ABAC-guarded)
│   ├── GeoIpService.java                 # IP lookup, client IP extraction
│   ├── ABACRulesService.java             # grantAccessFor + enforceIpBinding (ABAC: self/ADMIN→CUSTOMER; ADMIN→ADMIN denied)
│   ├── VerificationCodeStore.java        # Redis-based verification code storage (15 min TTL, key prefix "verification:")
│   ├── redis/
│   │   └── FailedLoginTracker.java       # Redis-based failed login counter (12 h TTL, key prefix "failed_logins:"), reset() clears counter
│   └── mail/
│       ├── AuthMailService.java          # composes + dispatches verification emails (token→Redis→link→send)
│       ├── EmailService.java             # email service interface
│       └── EmailSenderService.java       # email service implementation (Thymeleaf + @Async("mailExecutor"))
└── validator/
    ├── DataValidator.java                # low-level format checks (email, name, username, password, IP)
    ├── AuthValidator.java                # auth + change-password/email validation rules
    ├── UserValidator.java                # user update validation rules
    └── HostValidator.java                # host list filter validation (page size bounds)

docs/
├── api/api.yaml          # OpenAPI 3.0.3 spec (source of truth for endpoints)
└── mcd.canvas            # Obsidian data model canvas

src/main/resources/
├── application.properties
├── db/migration/
│   ├── V1__init.sql           # native DDL: user_role enum, user_status enum, "user" table
│   ├── V2__host.sql           # host_status enum, "host" table
│   └── V3__event_log.sql     # "event_log" table
├── geoip/
│   └── GeoLite2-City.mmdb     # MaxMind GeoIP database
└── templates/
    └── mail/
        ├── verification.html              # registration verification email
        ├── login-verification.html        # login verification email
        ├── unlock-account.html            # account unlock email
        ├── account-locked.html            # account locked (security alert) email
        ├── change-email.html              # confirm new email address email
        └── password-change.html           # password changed notification email
```

## Domain entities

| Table    | Purpose                                   | Key columns                                                     |
|----------|-------------------------------------------|-----------------------------------------------------------------|
| `users`  | User accounts with JWT auth               | `id` (UUID PK), `username`, `email`, `password`, `role`, `status`, `verified` |
| `host`   | IP address tracking per user              | `id` (UUID PK), `user_id` (FK), `ip_address` (unique), `status`, `last_seen_at`, `updated_at` |
| `event_log` | Authentication event audit trail       | `id` (UUID PK), `host_id` (FK), `user_agent`, `status`, `description`, `created_at`    |

**Enums:**
- `user_role`: `ADMIN`, `CUSTOMER`
- `user_status`: `ACTIVE`, `INACTIVE`, `LOCKED`
- `host_status`: `AUTHORIZED`, `BANNED`

**Schema**: native PostgreSQL DDL (`V1__init.sql`, `V2__host.sql`, `V3__event_log.sql`), schema `jwt_template_app` (set via `.env`). Applied manually, not via Flyway. Password hashing: Argon2id (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).

## OpenAPI spec endpoints

| Method | Path                           | Auth | Description                                                                                  |
|--------|--------------------------------|------|----------------------------------------------------------------------------------------------|
| GET    | /syn                           | —    | Health check → 200 `syn-ack`                                                                 |
| POST   | /auth/register                 | —    | Register → 202, sends verification code by email                                             |
| GET    | /auth/verification/{token}     | —    | Verify token → 200 with JWT token + user. Consumes tokens from all verification flows        |
| POST   | /auth/login                    | —    | Login → 202, sends verification code by email                                                |
| POST   | /auth/resend-link              | —    | Resend verification code → 202                                                              |
| POST   | /auth/change-password          | JWT  | Request password change → 202                                                                 |
| POST   | /auth/change-email             | JWT  | Request email change → 202                                                                     |
| POST   | /auth/unlock                   | —    | Recovery: unlock a LOCKED account → 202                                                       |
| POST   | /auth/logout                   | JWT  | Revoke current token (logout) → 200                                                           |
| GET    | /users/{userId}/hosts          | JWT  | List hosts (paginated). ABAC-guarded: self-access or ADMIN→CUSTOMER; IP binding enforced |
| GET    | /users/{userId}/hosts/{hostId} | JWT  | Get host by ID with GeoIP data *(spec'd, not yet implemented)*                              |
| PATCH  | /users/{userId}/hosts/{hostId} | JWT  | Ban a host *(spec'd, not yet implemented)*                                                   |
| GET    | /geoip                         | —    | Resolve client geolocation (X-Forwarded-For)                                                 |
| GET    | /geoip/{ip}                    | —    | Resolve IP geolocation (IPv4/IPv6)                                                           |
| GET    | /users                         | JWT  | List users (paginated, admin only)                                                          |
| GET    | /users/{userId}                | JWT  | Get user by ID                                                                              |
| PATCH  | /users/{userId}                | JWT  | Update user                                                                                 |
| DELETE | /users/{userId}                | JWT  | Delete user                                                                                 |

## Infrastructure

### Database (PostgreSQL)
- Native DDL in `src/main/resources/db/migration/` — applied manually, not via Flyway
- Three migration files: `V1__init.sql` (user_role, user_status, user), `V2__host.sql` (host_status, host), `V3__event_log.sql` (event_log)
- Schema: `jwt_template_app` (configured via `spring.jpa.properties.hibernate.default_schema` in `.env`)
- DDL creates enum `user_role` (ADMIN, CUSTOMER), enum `user_status` (ACTIVE, INACTIVE, LOCKED), enum `host_status` (AUTHORIZED, BANNED)
- Tables: `"user"` (UUID PK, `username`, `email`, `password`, `role`, `status`, `verified`), `host` (UUID PK, FK to user, unique `ip_address`, `status`, `last_seen_at`, `updated_at`), `event_log` (UUID PK, FK to host, `user_agent`, `status`, `description`, `created_at`)
- Password encoding: Argon2id via Spring Security's `Argon2PasswordEncoder`

### Redis
- Two uses:
  - **Verification code storage** (`VerificationCodeStore`): 15-minute TTL, key pattern `verification:{token}` → email address
  - **Failed-login tracking** (`FailedLoginTracker`): 12-hour TTL, key pattern `failed_logins:{userId}` → count. After 5 failures account is locked
- Implemented using `StringRedisTemplate`
- Compatible with Upstash (serverless) and self-hosted RESP-compatible Redis
- Configure via `spring.data.redis.url` in `.env`

### Email
- Gmail SMTP (smtp.gmail.com:587, STARTTLS)
- Async sending via `@Async("mailExecutor")` — dedicated `ThreadPoolTaskExecutor` (core:5, max:20, queue:100)
- Thymeleaf HTML templates in `src/main/resources/templates/mail/`
- Email variables: `verificationUrl`, `unlockUrl`, `firstName`, `lastName`, `username`, `email`, `oldEmail`, `clientIp`, `userAgent`, `time`, `city`, `country`, `countryCode`, `timezone`, `latitude`, `longitude`
- `MailSendException` caught by `GlobalExceptionHandler` — returns generic message, logs detail

### GeoIP
- MaxMind GeoIP2 4.2.1 — `DatabaseReader` bean in `GeoIpConfig`
- Two deployment modes:
  - **Bundled**: `classpath:geoip/GeoLite2-City.mmdb` read via `Resource.getInputStream()`
  - **NFS-mounted**: `file:/mnt/geoip/GeoLite2-City.mmdb` from a VPS
- Client IP: `X-Forwarded-For` header (first IP) when `geoip.trust-x-forwarded-for=true`, else `getRemoteAddr()`

### Environment variables (.env)

Spring loads `.env` via `spring.config.import=optional:file:.env[.properties]`, which parses it as a standard Java properties file (key=value, one per line — not shell `export VAR=value`). All sensitive config goes here; the file is gitignored.

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
# Base URL for building verification links
app.base-url=

# Redis (Upstash or self-hosted — use rediss:// for SSL/TLS)
spring.data.redis.url=

# GeoIP — bundled or NFS-mounted from VPS
# geoip.database-path=classpath:geoip/GeoLite2-City.mmdb
# geoip.database-path=file:/mnt/geoip/GeoLite2-City.mmdb
```

**Optional overrides** (have defaults in `application.properties`):

| Property | Default | When to override |
|----------|---------|------------------|
| `geoip.database-path` | `classpath:geoip/GeoLite2-City.mmdb` | When using NFS-mounted MMDB |
| `geoip.trust-x-forwarded-for` | `true` | When behind a proxy that should not be trusted |
| `app.jwt.expiration-ms` | `86400000` (24h) | To change token lifetime |

**Gmail SMTP setup**: enable 2-Step Verification → generate an [App Password](https://myaccount.google.com/apppasswords) → set `spring.mail.username` + `spring.mail.password` in `.env`. Host/port are in `application.properties`.

## Common commands

```bash
# build (JDK 26 toolchain required)
JAVA_HOME=$HOME/.jdks/openjdk-26.0.2.1 ./gradlew build

# test
JAVA_HOME=$HOME/.jdks/openjdk-26.0.2.1 ./gradlew test

# run
JAVA_HOME=$HOME/.jdks/openjdk-26.0.2.1 ./gradlew bootRun
```

> No Spotless/format task is wired up — follow the Code style convention below manually. `JAVA_HOME` must point to the installed JDK 26 (`$HOME/.jdks/openjdk-26.0.2.1`); there is no `java` on PATH by default.

## Security model

### Authentication (stateless JWT)
1. `JwtTokenProvider` creates tokens with subject (userId), role, and `ip_address` claims, signed with HMAC-SHA using a BASE64-decoded secret (`app.jwt.secret`). Expiration configurable via `app.jwt.expiration-ms` (default: 24h).
2. `JwtAuthenticationFilter` (OncePerRequestFilter) extracts Bearer token from `Authorization` header, validates it, sets `SecurityContext` with `UsernamePasswordAuthenticationToken` containing userId + `ROLE_ADMIN`/`ROLE_CUSTOMER` authority + IP as auth details.
3. No `UserDetailsService` — purely claim-based. Invalid/expired tokens silently ignored.

### Authorization (SecurityFilterChain)
- `/auth/**`, `/syn`, `/geoip/**` → `permitAll()`
- `/users`, `/users/**` → `authenticated()` (covers `/users/{userId}`, `/users/{userId}/hosts`, `/users/{userId}/hosts/{hostId}`)
- Unauthenticated → 401, unauthorized → 403

### Fine-grained access control (`ABACRulesService`)
Injected into services, called before operations. Rules:
- **Self-access**: requesterId == targetId
- **ADMIN → CUSTOMER**: admin can access customer resources
- **ADMIN → ADMIN**: denied
- **CUSTOMER → anything else**: denied
- **IP binding**: if JWT carries `ip_address` claim, current request IP must match. Mismatch → 403 `Session IP does not match current request`

### Logout (token revocation)
`POST /auth/logout` (JWT) adds the caller's current token to a Redis blacklist (TTL = remaining token lifetime). `JwtAuthenticationFilter` rejects blacklisted tokens, so the token becomes unusable immediately and the revocation lapses when the token would have expired. The client should discard the token after a successful response.

### Failed login tracking & account lockout
`FailedLoginTracker` (Redis): counts failed attempts per userId, key `failed_logins:{userId}`, TTL 12h. After **5 failed attempts** the account status is set to `LOCKED` (`UserStatus.LOCKED`) and login is rejected with 403 `Account locked`. On the 5th failure a notification email is sent via `AuthMailService.sendAccountLockedNotification()` using the `account-locked` template.

### Host tracking & event logging
On each authentication attempt, `AuthService.recordHostAndCheckBan()` looks up or creates a `Host` record (ip_address + user_agent + user_id), checks if host is `BANNED` (→ 403), saves it, and logs the event to `event_log`. Host status is marked as "unreliable" in the code; only the BANNED check is enforced.

## Verification flow

**Registration**: `POST /auth/register` → save user (verified=false) → generate UUID token → store in Redis (15 min TTL) → send verification email → user clicks link → `GET /auth/verification/{token}` → validate token → check host not banned → set verified=true → return JWT + user.

**Login**: `POST /auth/login` → validate credentials (username or email + password) → check host not banned → check verified=true → check not locked → generate UUID token → store in Redis (15 min TTL) → send verification email → user clicks link → `GET /auth/verification/{token}` → validate token → check host not banned → return JWT + user.

**Resend**: `POST /auth/resend-link?email=...` → validate email → find unverified user → generate new token → store in Redis → send email.

**Unlock** (account locked): `POST /auth/unlock` (public recovery, no JWT) → validate email → find LOCKED account (else 403, enumeration-safe) → check requesting host not banned → generate token → store in Redis → send unlock email → user clicks link → `GET /auth/verification/{token}` → validate → set status ACTIVE + reset failed-login counter → return JWT.

**Change password**: `POST /auth/change-password` (JWT) → validate current credentials + new password (block reuse) → generate token → send confirmation email → click link → `GET /auth/verification/{token}` → update password → return JWT.

**Change email**: `POST /auth/change-email` (JWT) → validate current credentials + new email → generate token → send confirmation email to new address → click link → `GET /auth/verification/{token}` → update email → return JWT.

All verification flows consume tokens via the same `GET /auth/verification/{token}` endpoint. Redis key pattern: `verification:{token}` → email address, TTL 15 minutes.

> **Implementation note**: register, login, resend, unlock, change-password, and change-email all generate tokens via `AuthMailService` and consume them through the single `GET /auth/verification/{token}` endpoint, which handles every token-consuming flow generically. `VerificationCodeStore.savePendingEmail(token, email)` carries the pending email for the change-email flow.

## Conventions

- **IDs**: all UUIDs (`gen_random_uuid()`, `java.util.UUID`)
- **Package**: `com.techindna.springbootjwttemplate`
- **Layer naming**: J-prefix for JPA entities (`JUser`, `JHost`, `JEventLog`), domain records in `entity/`, Lombok `@Getter @Setter @NoArgsConstructor`
- **Validation**: `DataValidator` (format) + `AuthValidator`/`UserValidator` (rules) — void return, throw `UnprocessableContentException` (422), not `@Valid`
- **Error handling**: custom exceptions → `GlobalExceptionHandler` → JSON `ErrorBody` (status, error, message, timestamp)
- **Mail exceptions**: `MailSendException` (Spring) — handler returns generic message, logs detail
- **JWT auth**: claim-based — extract `userId` + `role` + `ip_address` from token, no `UserDetailsService`
- **Async**: `@EnableAsync` + `@Async("poolName")` on service methods, dedicated `ThreadPoolTaskExecutor` per domain in `AsyncConfig`
- **Resources access**: `ABACRulesService` — inject, call `grantAccessFor(userId, request)` before operations and `enforceIpBinding(auth, request)` on JWT endpoints. Self-access, ADMIN→CUSTOMER; ADMIN→ADMIN denied; IP binding enforced
- **OpenAPI pagination**: `{data: [...], meta: {page (1-indexed), size, total}}`
- **API prefix**: no global prefix — each controller sets its own (`/auth`, `/users`, `/syn`, `/geoip`)
- **GeoIP**: MaxMind MMDB — either `classpath:geoip/GeoLite2-City.mmdb` (bundled) or `file:/mnt/geoip/GeoLite2-City.mmdb` (NFS-mounted). Update manually — re-download from MaxMind and replace.
- **Docs language**: English for API descriptions, French for user-facing instructions
- **Commits**: one commit per logical change, conventional format
- **Code style**: English-only, no comments/docstrings, short focused functions, explicit constructors over `@AllArgsConstructor`
