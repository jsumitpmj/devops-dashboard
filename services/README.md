# Services — Java Backend

This directory contains the Maven multi-module backend for the DevOps Dashboard Platform.

## Module Structure

```
services/
├── pom.xml                # Parent POM (Spring Boot 3.4, Java 21)
├── shared/                # Shared library — NOT a runnable service
│   └── src/main/java/
│       ├── model/         # JPA entities (User, Role, Project, Environment, Deployment, Alert, AuditLog)
│       ├── repository/    # Spring Data JPA repositories
│       ├── dto/           # Shared DTOs (ApiError, HealthResponse, PaginatedResponse)
│       ├── security/      # JwtUtil, JwtAuthenticationFilter
│       ├── health/        # HealthChecker (DB connectivity)
│       └── exception/     # GlobalExceptionHandler
├── api/                   # Read gateway (:4000) — projects, environments, deployments, alerts, audit logs
├── auth/                  # Auth (:4001) — register, login, JWT, /me
├── deployment/            # Deployment lifecycle (:4002) — create, status transitions
├── notification/          # Notification (:4003) — email/slack handlers, strategy pattern
└── worker/                # Background jobs (:4004) — polling, escalation, sync
```

## Dependency Graph

```
shared ← api
shared ← auth
shared ← deployment
shared ← notification
shared ← worker
```

All service modules depend on `shared`. No service depends on another service's code.
Inter-service communication happens via REST (worker → notification).

## Build Commands

```bash
cd services

# Build all
mvn clean install -DskipTests

# Build specific service
mvn clean package -pl shared,api -am -DskipTests

# Run tests
mvn clean test

# Run specific service
mvn spring-boot:run -pl api
```

## Shared Module

The `shared` module is a plain JAR (not a Spring Boot app). It provides:

| Package | Contents |
|---------|----------|
| `model` | 7 JPA entities mapped to PostgreSQL tables |
| `repository` | Spring Data JPA repositories with custom queries |
| `dto` | `ApiError` (structured errors), `HealthResponse`, `PaginatedResponse` |
| `security` | `JwtUtil` (generate/parse/validate), `JwtAuthenticationFilter` (OncePerRequestFilter) |
| `health` | `HealthChecker` — DB ping with latency measurement |
| `exception` | `GlobalExceptionHandler` — catches validation, auth, and general errors |

Each service includes `shared` via `@ComponentScan` and `@EntityScan` annotations.
