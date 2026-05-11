# kLo

kLo is a Spring Boot application for Lotto 6/45 winning-number lookup, collection, and rule-based number recommendation.

The project provides public APIs for reading stored draw data, administrator APIs for collecting or refreshing draw results, and a simple Thymeleaf-based web entry point. Recommendation output is generated from configurable rules and random combinations; it does not predict or guarantee winning numbers.

## Project Overview

kLo stores Lotto 6/45 draw results in MariaDB and exposes them through REST APIs. Draw data can be collected from the configured lottery API client, refreshed manually through protected administrator endpoints, and collected automatically through scheduled jobs.

The application is designed as a conventional server-rendered Spring Boot service with a REST API layer, service layer, persistence layer, Flyway-managed schema migrations, Docker-based local infrastructure, and health endpoints for deployment checks.

## Key Features

- Lotto 6/45 winning-number lookup by latest draw, specific round, and paginated list
- Number frequency statistics based on stored winning-number data
- Rule-based recommendation API with configurable exclusion thresholds
- Manual draw collection and refresh through administrator-only endpoints
- Scheduled draw collection and missing-draw correction jobs using the `Asia/Seoul` time zone
- Configurable real/mock lottery API client and optional mock fallback behavior
- MariaDB persistence using Spring Data JPA and Flyway migrations
- Thymeleaf + Bootstrap web page entry point
- Spring Security filter-based admin token protection and API rate limiting
- Actuator health, readiness, liveness, metrics, and Docker health checks

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle 9.4.1, Kotlin DSL |
| Web | Spring MVC, Thymeleaf, Bootstrap 5, WebJars |
| Persistence | Spring Data JPA, MariaDB 11.x, Flyway |
| Security | Spring Security, custom admin token filter, rate-limit filter |
| Observability | Spring Boot Actuator, Micrometer |
| Testing | JUnit 5, Spring Boot Test, Spring Security Test, REST Docs, Testcontainers, H2, ArchUnit |
| Runtime | Docker, Docker Compose, Eclipse Temurin 25 |

## Architecture Summary

The codebase is organized around feature modules and supporting infrastructure.

- `feature/winningnumber`: winning-number domain, collection, query, persistence, scheduling, and API code
- `feature/recommend`: recommendation rules and recommendation API code
- `infra`: configuration, security filters, API clients, and external integration support
- `support`: shared API response and exception handling components
- `web`: Thymeleaf page entry controller
- `resources/db/migration`: Flyway database migrations

At runtime, the application follows this flow:

```text
Client / Browser
  -> Spring MVC Controller
  -> Application Service
  -> Domain / Rule Logic
  -> Repository / External API Client
  -> MariaDB / DHLottery-compatible API endpoint
```

## Setup Instructions

### Prerequisites

- JDK 25
- Docker and Docker Compose
- Git

### Clone the repository

```bash
git clone https://github.com/portuna85/kLo.git
cd kLo
```

### Create a local environment file

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Update `.env` before running the application. At minimum, replace the default database passwords and administrator token values.

For local Docker development, the default profile is `local` and the default API client is `mock`. To collect real draw data, set:

```env
KRAFT_API_CLIENT=real
```

## Environment Variables

| Variable | Required | Description | Default / Example |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | Active Spring profile | `local` |
| `KRAFT_APP_PORT` | No | Host port mapped to the application container | `8080` |
| `KRAFT_DB_PORT` | No | Host port mapped to MariaDB | `3306` |
| `KRAFT_DB_NAME` | Yes | MariaDB database name | `kraft_lotto` |
| `KRAFT_DB_URL` | Yes | JDBC URL used by Spring | `jdbc:mariadb://mariadb:3306/kraft_lotto...` |
| `KRAFT_DB_USER` | Yes | Database username | `kraft` |
| `KRAFT_DB_PASSWORD` | Yes | Database password | `change-me` |
| `KRAFT_DB_ROOT_PASSWORD` | Yes | MariaDB root password | `change-me-root` |
| `KRAFT_DB_LOCAL_HOST` | No | Host replacement used for local non-container runs | `localhost` |
| `KRAFT_IN_CONTAINER` | No | Indicates whether the app is running inside Docker | `false` / `true` |
| `KRAFT_API_CLIENT` | No | Lottery API client mode | `mock` or `real` |
| `KRAFT_API_URL` | No | Lottery API base URL | `https://www.dhlottery.co.kr/common.do` |
| `KRAFT_API_CONNECT_TIMEOUT_MS` | No | External API connect timeout | `3000` |
| `KRAFT_API_READ_TIMEOUT_MS` | No | External API read timeout | `5000` |
| `KRAFT_API_MAX_RETRIES` | No | External API retry count | `2` |
| `KRAFT_API_RETRY_BACKOFF_MS` | No | External API retry backoff | `700` |
| `KRAFT_API_FALLBACK_TO_MOCK_ON_FAILURE` | No | Use mock data if the real API fails | `false` (`true` in local profile) |
| `KRAFT_API_MOCK_LATEST_ROUND` | No | Latest mock draw round | `1200` |
| `KRAFT_ADMIN_API_TOKEN` | Yes for admin APIs | Token required for protected collection APIs | `change-me-admin-token` |
| `KRAFT_ADMIN_TOKEN_HEADER` | No | Header name for the admin token | `X-Kraft-Admin-Token` |
| `KRAFT_LOTTO_SCHEDULER_ENABLED` | No | Enables Lotto scheduler components | `true` |
| `KRAFT_COLLECT_AUTO_ENABLED` | No | Enables automatic collection jobs | `true` |
| `KRAFT_COLLECT_AUTO_ZONE` | No | Collection scheduler time zone | `Asia/Seoul` |
| `KRAFT_COLLECT_AUTO_CRON_SATURDAY_21_10` | No | Saturday first collection schedule | `0 10 21 ? * SAT` |
| `KRAFT_COLLECT_AUTO_CRON_SATURDAY_21_RETRY` | No | Saturday retry schedule | `0 20,40 21 ? * SAT` |
| `KRAFT_COLLECT_AUTO_CRON_SATURDAY_22_10` | No | Saturday late retry schedule | `0 10 22 ? * SAT` |
| `KRAFT_COLLECT_AUTO_CRON_SUNDAY_06_10` | No | Sunday correction schedule | `0 10 6 ? * SUN` |
| `KRAFT_COLLECT_AUTO_CRON_DAILY_09_00` | No | Daily missing-draw correction schedule | `0 0 9 * * *` |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | No | Maximum attempts for recommendation generation | `5000` |
| `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS` | No | Recommendation API rate-limit count | `30` |
| `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS` | No | Recommendation API rate-limit window | `60` |
| `KRAFT_COLLECT_RATE_LIMIT_MAX_REQUESTS` | No | Collection API rate-limit count | `10` |
| `KRAFT_COLLECT_RATE_LIMIT_WINDOW_SECONDS` | No | Collection API rate-limit window | `300` |
| `KRAFT_LOG_PATH` | No | Log file directory | `./logs` |
| `KRAFT_HEALTHCHECK_URL` | No | Docker health-check URL | `http://localhost:8080/actuator/health/readiness` |
| `KRAFT_HEALTHCHECK_TIMEOUT_SECONDS` | No | Docker health-check timeout | `3` |

Do not commit `.env` or production secrets.

## Local Run Commands

### Run with Docker Compose

```bash
docker compose up -d --build
```

Application URL:

```text
http://localhost:8080
```

Stop containers:

```bash
docker compose down
```

Remove containers and the MariaDB volume:

```bash
docker compose down -v
```

### Run with Gradle

When running outside Docker, ensure the required environment variables are available in your shell or IDE.

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### Build the application JAR

```bash
./gradlew clean bootJar
```

Windows PowerShell:

```powershell
.\gradlew.bat clean bootJar
```

The default executable artifact is:

```text
build/libs/app.jar
```

## Test Commands

Run the test suite:

```bash
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

Build without tests:

```bash
./gradlew clean bootJar -x test
```

Generate REST Docs and build a documentation-including JAR:

```bash
./gradlew bootJarWithDocs
```

Verify that the production profile file is packaged:

```powershell
.\gradlew.bat clean bootJar -x test
.\scripts\verify-prod-profile-in-jar.ps1
```

Manual JAR profile check:

```bash
jar tf build/libs/app.jar | grep application
```

Expected profile files include:

```text
BOOT-INF/classes/application.yml
BOOT-INF/classes/application-local.yml
BOOT-INF/classes/application-prod.yml
```

## API Overview

All REST APIs return a common API response wrapper.

### Public APIs

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/winning-numbers/latest` | Get the latest stored winning number |
| `GET` | `/api/winning-numbers/{round}` | Get a stored winning number by round |
| `GET` | `/api/winning-numbers?page=0&size=20` | Get paginated winning numbers |
| `GET` | `/api/winning-numbers/stats/frequency` | Get number frequency statistics |
| `POST` | `/api/recommend` | Generate recommended number combinations |
| `GET` | `/api/recommend/rules` | Get active recommendation rules |

Example recommendation request:

```bash
curl -X POST "http://localhost:8080/api/recommend" \
  -H "Content-Type: application/json" \
  -d '{"count":5}'
```

### Protected Collection APIs

The following APIs require the configured admin token header.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/winning-numbers/refresh` | Collect the next draw or a requested target round |
| `POST` | `/admin/lotto/draws/collect-next` | Collect the next expected draw |
| `POST` | `/admin/lotto/draws/collect-missing` | Collect missing stored draws |
| `POST` | `/admin/lotto/draws/{drwNo}/refresh` | Refresh one draw |
| `POST` | `/admin/lotto/draws/backfill?from=1&to=1200` | Backfill a draw range |

Example protected request:

```bash
curl -X POST "http://localhost:8080/api/winning-numbers/refresh" \
  -H "Content-Type: application/json" \
  -H "X-Kraft-Admin-Token: ${KRAFT_ADMIN_API_TOKEN}" \
  -d '{"targetRound":"1200"}'
```

### Health Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/actuator/health` | General health status |
| `GET` | `/actuator/health/liveness` | Liveness probe |
| `GET` | `/actuator/health/readiness` | Readiness probe |

## Deployment Notes

- Use `SPRING_PROFILES_ACTIVE=prod` for production deployments.
- Inject all secrets and environment-specific values through environment variables or deployment manifests.
- Do not hardcode credentials, API tokens, or production database URLs in repository files.
- The Docker image builds with JDK 25 and runs with JRE 25 as a non-root user.
- The application listens on port `8080` inside the container.
- MariaDB data is stored in a Docker volume for local Compose runs.
- Flyway migrations run on application startup when enabled.
- Check `/actuator/health/readiness` before routing traffic to the container.
- Confirm that `application-prod.yml` is included in `build/libs/app.jar` before deployment.
- For real draw collection, set `KRAFT_API_CLIENT=real` and keep retry/fallback behavior explicit.

## Project Structure

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── docker/
│   └── healthcheck.sh
├── scripts/
│   └── verify-prod-profile-in-jar.ps1
└── src/
    ├── main/
    │   ├── java/com/kraft/lotto/
    │   │   ├── feature/
    │   │   │   ├── recommend/
    │   │   │   └── winningnumber/
    │   │   ├── infra/
    │   │   ├── support/
    │   │   └── web/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       ├── db/migration/
    │       ├── static/
    │       └── templates/
    └── test/
        └── java/com/kraft/lotto/
```

## Future Improvement Roadmap

- Add generated OpenAPI documentation for REST endpoints
- Expand REST Docs coverage for public and admin APIs
- Improve the web UI for draw search, statistics, and recommendation results
- Add more operational metrics around collection success, failures, and retry behavior
- Add CI/CD workflows for test, build, image publishing, and deployment verification
- Replace simple admin token authentication with a stronger administrative access model if needed
- Add more explicit data backfill tooling and collection audit views
- Increase integration test coverage for scheduler, external API failure handling, and database migrations

## License

MIT License
