# Transaction API (Spring Boot)

A minimal Spring Boot 3 / Java 21 service for recording financial transactions across user accounts. Authentication is intentionally stubbed for now (use the `X-User-Id` header to simulate the caller). Flyway manages the PostgreSQL schema.

## Running locally

1. Start PostgreSQL and set environment variables (defaults in `application.properties`):
   - `DB_HOST` (default `localhost`)
   - `DB_PORT` (default `5432`)
   - `DB_NAME` (default `transactions`)
   - `DB_USERNAME` / `DB_PASSWORD`
2. Use `application-local.properties` for local overrides (profile `local`), and `application.properties` for shared defaults. Example local file:
   ```
   spring.datasource.url=jdbc:postgresql://localhost:5432/transactions
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   app.security.dev-user-id=local-user
   ```
   Run with `SPRING_PROFILES_ACTIVE=local` to pick it up.
3. Run migrations and start the app:
   ```bash
   mvn -B spring-boot:run
   ```

## API (early scaffold)

- `POST /api/v1/accounts` — create an account for the caller (`X-User-Id` header required).
- `GET /api/v1/accounts` — list caller accounts.
- `GET /api/v1/accounts/{id}` — fetch an account (only if owned by caller).
- `POST /api/v1/accounts/{id}/transactions` — create a transaction on the account.
- `GET /api/v1/accounts/{id}/transactions` — list transactions on the account.
- `GET /api/v1/health` — basic health check.

Transactions support richer trade fields (`ticker`, `name`, `currency`, `exchange`, `quantity`, `price`, option details, `fee`) and linkage via `relatedTransactionId` for transfers. When `optionType` is provided, `strikePrice`, `expiryDate`, and `underlyingTicker` are required; transaction types also cover option lifecycle events (e.g., exercises, expirations).

## Authentication scaffolding

- Default: stateless requests, `X-User-Id` header is accepted and turned into an authenticated principal. You can set a dev user via `app.security.dev-user-id` to avoid passing the header locally.
- JWT (preferred for real deployments): set `app.security.jwt.enabled=true` and provide either `JWT_ISSUER_URI` or `JWT_JWKS_URI` env vars. Spring Security will validate bearer tokens and use the JWT `sub` (or `email`) as the caller id. Works with providers like AWS Cognito, Firebase, Auth0, etc. By default those properties are unset to avoid auto-config errors locally.
- Health endpoint is open; all other endpoints require authentication.

## Code layout

- `model/` holds JPA entities.
- `constants/` holds enums.
- `dto/`, `service/`, `repository/`, `controller/`, `security/` are named as expected.

## Database migrations

Flyway runs migrations from `src/main/resources/db/migration`. Initial DDL lives in `V1__create_accounts_and_transactions.sql`. JPA is set to `validate` to keep entities and DDL in sync.

## CI/CD (GitHub Actions)

A starter workflow in `.github/workflows/ci.yml` builds and tests with Maven. It also contains a stubbed Docker/ECR deploy job for ECS—fill in AWS details and secrets before enabling.
