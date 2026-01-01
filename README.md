# Transaction API (Spring Boot)

Simple trade-tracking backend (Spring Boot 3 / Java 21). It only cares about trades and realized P/L; there are no accounts or cash flows. Authentication is intentionally light for now (use the `X-User-Id` header to identify the caller). Flyway manages the PostgreSQL schema.

## Running locally

1. Start PostgreSQL and set environment variables (defaults in `application.properties`):
   - `DATABASE_URL` `jdbc:postgresql://...`
2. Use `application-local.properties` for local overrides (profile `local`), and `application.properties` for shared defaults. Example local file:
   ```
   spring.datasource.url=jdbc:postgresql://localhost:5432/transactions
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
- `GET /api/v1/admin/users` — list users (admin only).

Trade fields are intentionally minimal: symbol, asset type (stock/option), direction (long/short), quantity, entry/exit prices, fees, open/close dates, notes, and option-specific details (type/strike/expiry). Realized P/L is calculated server-side on create/update.

## Authentication scaffolding

- Default: stateless requests, `X-User-Id` header is accepted and turned into an authenticated principal. You can set a dev user via `app.security.dev-user-id` to avoid passing the header locally.
- JWT (preferred for real deployments): set `app.security.jwt.enabled=true`, `app.security.jwt.issuer-uri=https://accounts.google.com`, and `app.security.jwt.audience=<Google client id>`. Spring Security validates bearer tokens and uses the JWT `sub` (or `email`) as the caller id.
- Admin allowlist: set `app.security.admin-emails` (comma-separated). If unset, the admin list falls back to `app.security.allowed-emails`.
- Health endpoint is open (`/api/v1/health` and `/`); all other endpoints require authentication.

## Database migrations

Flyway runs migrations from `src/main/resources/db/migration`. `V1__trades.sql` creates the single `trades` table used by the app.

## CI/CD (GitHub Actions)

CI runs on push/PR. Dev deploys automatically on `main` after tests pass. Prod deploys are manual via `workflow_dispatch`. Deploys update the App Runner service after pushing a new ECR image.

Required GitHub secrets (dev):
- `AWS_REGION`
- `AWS_ROLE_ARN`
- `DEV_ECR_TRANSACTION_API_REPO`
- `DEV_BACKEND_SERVICE_ARN` (App Runner service ARN)
- `DEV_DATABASE_URL`
- `DEV_CORS_ALLOWED_ORIGINS`
- `DEV_ALLOWED_EMAILS`
- `DEV_ADMIN_EMAILS` (optional)
- `DEV_GOOGLE_CLIENT_ID`
- `DEV_GOOGLE_JWK_SET` (optional)

Required GitHub secrets (prod):
- `PROD_ECR_TRANSACTION_API_REPO`
- `PROD_BACKEND_SERVICE_ARN` (App Runner service ARN)
- `PROD_DATABASE_URL`
- `PROD_CORS_ALLOWED_ORIGINS`
- `PROD_GOOGLE_CLIENT_ID`
- `PROD_GOOGLE_JWK_SET` (optional)
- `PROD_ADMIN_EMAILS` (optional)

OIDC role permissions for App Runner deploys must include:
- `apprunner:UpdateService`, `apprunner:DescribeService`

## Frontend

The companion frontend lives at https://github.com/alexmcdermid/tradingView.
