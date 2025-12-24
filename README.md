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
- JWT (preferred for real deployments): set `app.security.jwt.enabled=true` and provide either `JWT_ISSUER_URI` or `JWT_JWKS_URI` env vars. Spring Security will validate bearer tokens and use the JWT `sub` (or `email`) as the caller id. By default those properties are unset to avoid auto-config errors locally.
- Health endpoint is open; all other endpoints require authentication.

## Database migrations

Flyway runs migrations from `src/main/resources/db/migration`. `V1__trades.sql` creates the single `trades` table used by the app.

## CI/CD (GitHub Actions)

A starter workflow in `.github/workflows/ci.yml` builds and tests with Maven. It also contains a stubbed Docker/ECR deploy job for ECS—fill in AWS details and secrets before enabling.

## Frontend

The companion frontend lives at https://github.com/alexmcdermid/tradingView.
