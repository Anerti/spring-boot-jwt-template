# Spring Boot JWT Template

A production-ready Spring Boot starter template with JWT authentication, user management, email verification, and PostgreSQL persistence.

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
  - [Flyway Migrations](#flyway-migrations)
  - [Adding a Migration](#adding-a-migration)
- [API Endpoints](#api-endpoints)
  - [Authentication](#authentication)
  - [Users](#users)
- [Architecture](#architecture)
  - [Project Structure](#project-structure)
  - [Conventions](#conventions)

---

## Stack

Java 21 · Spring Boot 4.1.0 · Spring Data JPA · PostgreSQL · Flyway · Lombok · Gradle 9.5.1 · OpenAPI 3.0.3

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | System default may be JDK 26 — always use the toolchain |
| Gradle | 9.5.1+ | Bundled via `./gradlew` |
| PostgreSQL | 14+ | Local or Neon/Supabase |

## Configuration

### Environment Variables (.env)

All sensitive configuration lives in `.env` at the project root. This file is **gitignored** and must never be committed.

Create a `.env` file with the following variables:

```bash
# Database (PostgreSQL / Neon)
spring.datasource.url=
spring.datasource.username=
spring.datasource.password=
spring.flyway.schemas=

# Mail (Gmail SMTP)
spring.mail.username=
spring.mail.password=

# Schema init (optional)
spring.sql.init.schema=
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
cp .env.example .env   # then fill in your credentials
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

The application starts on `http://localhost:8080`. Flyway runs automatically on first startup, creating the schema and applying migrations.

### 4. Test

```bash
JAVA_HOME=$HOME/.jdks/ms-21.0.11 ./gradlew test
```

## Database

### Flyway Migrations

Schema management is handled by Flyway. Migrations live in:

```
src/main/resources/db/migration/
├── V1__init.sql          # enum + user table
├── V2__add_xxx.sql       # future migrations
```

On startup, Flyway:
1. Creates the schema if it does not exist (`spring.flyway.schemas`)
2. Creates the `flyway_schema_history` table
3. Applies all pending migrations in version order

### Adding a Migration

```bash
# Create a new versioned migration file
touch src/main/resources/db/migration/V2__descriptive_name.sql
```

**Rules:**
- Never edit an already-applied migration — create a new one
- File naming: `V{version}__{description}.sql` (two underscores before description)
- All objects are created in the schema defined by `spring.flyway.schemas`

## API Endpoints

Base URL: `http://localhost:8080`

### Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | — | Register → 202, sends verification code by email |
| GET | `/auth/verification` | — | Verify code → 200 with JWT token |
| POST | `/auth/login` | — | Login → 202, sends verification code by email |

### Users

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/users` | JWT | List users (paginated, admin only) |
| GET | `/users/{userId}` | JWT | Get user by ID |
| PUT | `/users/{userId}` | JWT | Update user |
| DELETE | `/users/{userId}` | JWT | Delete user |

Full OpenAPI spec: [`docs/api/api.yaml`](docs/api/api.yaml)

## Architecture

### Project Structure

```
src/main/java/com/techindna/springbootjwttemplate/
├── SpringBootJwtTemplateApplication.java
├── entity/                    # Domain records
├── exception/                 # Custom exceptions + GlobalExceptionHandler
├── repository/model/          # JPA entities (J-prefix)
└── service/mail/              # Email service (interface + impl)

src/main/resources/
├── application.properties     # Non-sensitive config
├── db/migration/              # Flyway SQL migrations
└── .env (gitignored)          # Secrets
```

### Conventions

- **IDs**: UUIDs everywhere (`gen_random_uuid()`, `java.util.UUID`)
- **Layer naming**: J-prefix for JPA entities (`JUser`), domain records in `entity/`
- **Validation**: `DataValidator` pattern (void return, throws `UnprocessableContentException`), not `@Valid`
- **Error handling**: custom exceptions → `GlobalExceptionHandler` → JSON `ErrorBody`
- **Async**: `ExecutorService.newVirtualThreadPerTaskExecutor()`, no `@Async`
- **Pagination**: `{data: [...], meta: {page, size, total}}` (1-indexed)
- **API prefix**: no global prefix — each controller sets its own (`/auth`, `/users`, `/syn`)
