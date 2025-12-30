# Transaction API (Spring Boot)

Simple trade-tracking backend (Spring Boot 3 / Java 21). It only cares about trades and realized P/L; there are no accounts or cash flows. Authentication is intentionally light for now (use the `X-User-Id` header to identify the caller). Flyway manages the PostgreSQL schema.

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

## API

- `GET /api/v1/health` — basic health check.
- `GET /api/v1/trades` — list trades for the caller.
- `POST /api/v1/trades` — create a trade (stocks or options, long or short).
- `PUT /api/v1/trades/{id}` — update a trade (must belong to caller).
- `DELETE /api/v1/trades/{id}` — remove a trade.
- `GET /api/v1/trades/summary` — realized P/L totals with daily and monthly buckets.

Trade fields are intentionally minimal: symbol, asset type (stock/option), direction (long/short), quantity, entry/exit prices, fees, open/close dates, notes, and option-specific details (type/strike/expiry). Realized P/L is calculated server-side on create/update.

## Authentication scaffolding

- Default: stateless requests, `X-User-Id` header is accepted and turned into an authenticated principal. You can set a dev user via `app.security.dev-user-id` to avoid passing the header locally.
- JWT (preferred for real deployments): set `app.security.jwt.enabled=true`, `app.security.jwt.issuer-uri=https://accounts.google.com`, and `app.security.jwt.audience=<Google client id>`. Spring Security validates bearer tokens and uses the JWT `sub` (or `email`) as the caller id.
- Health endpoint is open; all other endpoints require authentication.

## Database migrations

Flyway runs migrations from `src/main/resources/db/migration`. `V1__trades.sql` creates the single `trades` table used by the app.

## CI/CD (GitHub Actions)

CI runs on push/PR. Dev deploys automatically on `main` after tests pass. Prod deploys are manual via `workflow_dispatch`.

Required GitHub secrets (dev):
- `AWS_REGION`
- `AWS_ROLE_ARN`
- `DEV_ECR_TRANSACTION_API_REPO`
- `DEV_ECS_CLUSTER`
- `DEV_ECS_SERVICE`
- `DEV_ECS_TASK_FAMILY`
- `DEV_ECS_EXECUTION_ROLE_ARN`
- `DEV_ECS_TASK_ROLE_ARN` (optional)
- `DEV_ECS_LOG_GROUP` (optional)
- `DEV_DB_HOST`, `DEV_DB_PORT`, `DEV_DB_NAME`, `DEV_DB_USERNAME`, `DEV_DB_PASSWORD`
- `DEV_CORS_ALLOWED_ORIGINS`
- `DEV_ALLOWED_EMAILS`
- `DEV_GOOGLE_CLIENT_ID`
- `DEV_GOOGLE_JWK_SET` (optional)

Required GitHub secrets (prod):
- `PROD_ECR_TRANSACTION_API_REPO`
- `PROD_ECS_CLUSTER`
- `PROD_ECS_SERVICE`
- `PROD_ECS_TASK_FAMILY`
- `PROD_ECS_EXECUTION_ROLE_ARN`
- `PROD_ECS_TASK_ROLE_ARN` (optional)
- `PROD_ECS_LOG_GROUP` (optional)
- `PROD_DB_HOST`, `PROD_DB_PORT`, `PROD_DB_NAME`, `PROD_DB_USERNAME`, `PROD_DB_PASSWORD`
- `PROD_CORS_ALLOWED_ORIGINS`
- `PROD_GOOGLE_CLIENT_ID`
- `PROD_GOOGLE_JWK_SET` (optional)

OIDC role permissions for ECS deploys must include:
- `ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`, `ecs:DescribeTaskDefinition`
- `iam:PassRole` for the execution role (and task role if used)

## Frontend

The companion frontend lives at https://github.com/alexmcdermid/tradingView.
