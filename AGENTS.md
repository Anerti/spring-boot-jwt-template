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
│   ├── ResourcesAccessRules.java         # authorization: self-access, ADMIN→CUSTOMER; ADMIN→ADMIN denied
│   └── jwt/
│       ├── JwtAuthenticationFilter.java   # JWT filter (extracts userId + role from claims, no UserDetailsService)
│       └── JwtTokenProvider.java          # JWT create/parse (HMAC-SHA, BASE64 secret, configurable TTL)
├── controller/
│   ├── AuthController.java               # POST /auth/register, POST /auth/login, GET /auth/verification/{token}, POST /auth/resend-link
│   ├── GeoIpController.java              # GET /geoip, GET /geoip/{ip}
│   ├── SynController.java                # GET /syn
│   └── UserController.java               # GET/PATCH/DELETE /users/{userId}
├── dto/
│   ├── LoginInput.java                   # login request body
│   ├── MessageBody.java                  # { message } response
│   ├── RegisterInput.java                # register request body
│   ├── UpdateUserInput.java              # user update request body
│   └── VerifyRegistrationResponse.java   # token + user response
├── entity/
│   ├── GeoIpResponse.java               # GeoIP lookup result record
│   ├── User.java                         # domain record (read model)
│   ├── email/EmailDetails.java           # email details entity
│   └── enums/UserRole.java               # user role enum (ADMIN, CUSTOMER)
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
│   └── GeoIpMapper.java                 # CityResponse → GeoIpResponse
├── repository/
│   ├── AuthRepository.java               # JPA repository (findByEmail, findByUsername)
│   ├── UserRepository.java               # JPA repository (CRUD)
│   └── model/
│       └── JUser.java                    # JPA entity (PostgreSQL "user" table)
├── service/
│   ├── AuthService.java                  # register + login + verification + resend
│   ├── UserService.java                  # getUser + updateUser (with ResourcesAccessRules)
│   ├── GeoIpService.java                # IP lookup, client IP extraction
│   ├── VerificationCodeStore.java        # Redis-based verification code storage (15 min TTL, key prefix "verification:")
│   └── mail/
│       ├── EmailService.java             # email service interface
│       └── EmailSenderService.java       # email service implementation (Thymeleaf + @Async("mailExecutor"))
└── validator/
    ├── DataValidator.java                # low-level format checks (email, name, username, password, IP)
    └── UserValidator.java                # registration + login + update rules

docs/
├── api/api.yaml          # OpenAPI 3.0.3 spec (source of truth for endpoints)
└── mcd.canvas            # Obsidian data model canvas

src/main/resources/
├── application.properties
├── db/migration/
│   └── V1__init.sql           # native DDL: enum + user table
├── geoip/
│   └── GeoLite2-City.mmdb     # MaxMind GeoIP database
└── templates/
    └── mail/
        ├── verification.html              # registration email template
        └── login-verification.html        # login email template
```

## Domain entities

| Table    | Purpose                                   | Key columns                                                     |
|----------|-------------------------------------------|-----------------------------------------------------------------|
| `users`  | User accounts with JWT auth               | `id` (UUID PK), `username`, `email`, `password`, `role`, `verified` |

**Enum** `user_role`: `ADMIN`, `CUSTOMER`

**Schema**: native PostgreSQL DDL (`V1__init.sql`), schema `jwt_template_app` (set via `.env`). Applied manually, not via Flyway. Password hashing: Argon2id (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).

## OpenAPI spec endpoints

| Method | Path                | Auth | Description                                      |
|--------|---------------------|------|--------------------------------------------------|
| GET    | /syn                | —    | Health check → 200 `syn-ack`                     |
| POST   | /auth/register      | —    | Register → 202, sends verification code by email |
| GET    | /auth/verification/{token} | —    | Verify token → 200 with JWT token                 |
| POST   | /auth/login         | —    | Login → 202, sends verification code by email    |
| POST   | /auth/resend-link   | —    | Resend verification code → 202                    |
| GET    | /geoip              | —    | Resolve client geolocation (X-Forwarded-For)      |
| GET    | /geoip/{ip}         | —    | Resolve IP geolocation (IPv4/IPv6)                |
| GET    | /users              | JWT  | List users (paginated, admin only)               |
| GET    | /users/{userId}     | JWT  | Get user by ID                                   |
| PATCH  | /users/{userId}     | JWT  | Update user                                      |
| DELETE | /users/{userId}     | JWT  | Delete user                                      |

## Infrastructure

### Database (PostgreSQL)
- Native DDL in `src/main/resources/db/migration/V1__init.sql` — applied manually, not via Flyway
- Schema: `jwt_template_app` (configured via `spring.jpa.properties.hibernate.default_schema` in `.env`)
- DDL creates enum `user_role` (ADMIN, CUSTOMER) and table `"user"` with UUID PK
- Password encoding: Argon2id via Spring Security's `Argon2PasswordEncoder`

### Redis
- Stores verification tokens with 15-minute TTL
- Key pattern: `verification:{token}` → email address
- Implemented in `VerificationCodeStore` using `StringRedisTemplate`
- Compatible with Upstash (serverless) and self-hosted RESP-compatible Redis
- Configure via `spring.data.redis.url` in `.env`

### Email
- Gmail SMTP (smtp.gmail.com:587, STARTTLS)
- Async sending via `@Async("mailExecutor")` — dedicated `ThreadPoolTaskExecutor` (core:5, max:20, queue:100)
- Thymeleaf HTML templates in `src/main/resources/templates/mail/`
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
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew build

# test
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew test

# run
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew bootRun

# format
JAVA_HOME=$HOME/.jdks/ms-26.0.2 ./gradlew spotlessApply
```

> `JAVA_HOME` must point to JDK 26 — the system default is JDK 26.

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
- **API prefix**: no global prefix — each controller sets its own (`/auth`, `/users`, `/syn`, `/geoip`)
- **GeoIP**: MaxMind MMDB — either `classpath:geoip/GeoLite2-City.mmdb` (bundled in `src/main/resources/geoip/`) or `file:/mnt/geoip/GeoLite2-City.mmdb` (NFS-mounted from a VPS). Configured via `geoip.database-path` in `.env`/`application.properties`. Update the file manually — re-download from MaxMind and replace it (and for NFS, re-export on the VPS).
- **Docs language**: English for API descriptions, French for user-facing instructions
- **Commits**: one commit per logical change, conventional format
- **Code style**: English-only, no comments/docstrings, short focused functions, explicit constructors over `@AllArgsConstructor`

