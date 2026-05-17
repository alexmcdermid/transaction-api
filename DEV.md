# Development Guide - Transaction API

## Running locally

1. Start PostgreSQL and set environment variables (defaults in `application.properties`):
   - `DATABASE_URL` (JDBC URL; put credentials in query params, not `user:pass@host`)
     - Example: `jdbc:postgresql://<host>/<db>?user=<user>&password=<url-encoded>&sslmode=require&channel_binding=require`

2. Use `application-local.properties` for local overrides (profile `local`), and `application.properties` for shared defaults. Example local file:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/transactions
   app.security.dev-user-id=local-user
   ```
   Run with `SPRING_PROFILES_ACTIVE=local` to pick it up.

3. Run migrations and start the app:
   ```bash
   mvn -B spring-boot:run
   ```

## API Endpoints

### Public
- `GET /api/v1/health` — basic health check
- `GET /api/v1/auth/csrf` — returns the CSRF header name and token for browser session requests
- `POST /api/v1/auth/login` — validates a Google credential and creates the session cookie
- `POST /api/v1/auth/logout` — invalidates the session cookie

### Authenticated
- `GET /api/v1/trades` — list trades for the caller
- `GET /api/v1/trades/paged` — paginated trades with optional month filter
- `POST /api/v1/trades` — create a trade (stocks or options, long or short)
- `PUT /api/v1/trades/{id}` — update a trade (must belong to caller)
- `DELETE /api/v1/trades/{id}` — remove a trade
- `GET /api/v1/trades/summary` — realized P/L totals with daily and monthly buckets (optionally filtered by month)
- `GET /api/v1/trades/stats` — aggregate statistics (total P/L, trade count, best day, best month) with CAD to USD conversion
- `GET /api/v1/trades/share/{token}` — view shared trade by token

### Admin Only
- `GET /api/v1/admin/users` — list users

Trade fields are intentionally minimal: symbol, asset type (stock/option), currency (USD/CAD), direction (long/short), quantity, entry/exit prices, fees, open/close dates, notes, and option-specific details (type/strike/expiry). Realized P/L is calculated server-side on create/update.

## Testing

Run all tests:
```bash
mvn test
```

Run specific test class:
```bash
mvn test -Dtest=TradeServiceTest
```

## Authentication

### Browser Session Mode
The frontend posts the Google credential to `POST /api/v1/auth/login`. The backend validates the credential with the configured `JwtDecoder`, enforces the allowed-email list, creates/loads the user, and stores an `AuthenticatedUserPrincipal` in the HTTP session.

Session cookies are configured as:
- `HttpOnly`
- `Secure` by default (`APP_SESSION_COOKIE_SECURE=false` only for local HTTP)
- `SameSite=Lax`
- timeout from `APP_SESSION_TIMEOUT` (default `PT2H`)

CSRF protection is enabled for unsafe cookie-session requests. Browser clients should fetch `GET /api/v1/auth/csrf` and send the returned header on `POST`, `PUT`, `PATCH`, and `DELETE` requests.

### Development Mode (Header-based)
- Header auth is disabled by default. For local/dev-only header auth, set `app.security.allow-header-auth=true`
- When enabled, stateless requests can use `X-User-Id` as the authenticated principal
- Header auth only works under the `local` or `test` Spring profiles
- Set `app.security.dev-user-id=local-user` to avoid passing the header locally
- Health endpoint is open (`/api/v1/health` and `/`); all other endpoints require authentication

### Production Mode
Set the following properties:
- `app.security.jwt.enabled=true`
- `app.security.jwt.issuer-uri=https://accounts.google.com`
- `app.security.jwt.audience=<Google client id>`
- `app.security.allow-header-auth=false`
- `app.security.admin-emails=<comma-separated-admin-emails>`

Login validates the Google credential and stores the Google `sub`, email, and name in the server session. Bearer token authentication is still supported for non-browser clients, and CSRF is skipped for explicit `Authorization` header requests.

### Admin Access
- `app.security.admin-emails` (comma-separated list)
- Admin emails are explicit; allowed users are not admins unless they are also listed here.

## Database migrations

Flyway runs migrations from `src/main/resources/db/migration`:
- `V1__trades.sql` — creates the trades table
- `V2__share_tokens.sql` — adds share token functionality
- `V3__add_currency.sql` — adds currency field for CAD/USD support
- `V4__optimize_aggregate_queries.sql` — adds performance indexes for aggregate stats

## Environment Variables (Production)

Required:
- `DATABASE_URL` (JDBC URL with credentials in query params)
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_SECURITY_JWT_ENABLED=true`
- `APP_SECURITY_ALLOW_HEADER_AUTH=false`
- `APP_SECURITY_JWT_ISSUER_URI=https://accounts.google.com`
- `APP_SECURITY_JWT_AUDIENCE=<Google client id>`
- `APP_SECURITY_JWT_JWK_SOURCE=dynamo`
- `APP_SECURITY_JWT_DYNAMO_TABLE=AuthJwks`
- `APP_SECURITY_JWT_DYNAMO_KEY=google`
- `APP_AWS_REGION=<AWS region>`
- `APP_FX_SOURCE=dynamo`
- `APP_FX_DYNAMO_TABLE=ExchangeRates`

Optional:
- `APP_SECURITY_ALLOWED_EMAILS` (comma-separated allowlist)
- `APP_SECURITY_ADMIN_EMAILS` (comma-separated admin allowlist)
- `APP_SECURITY_JWT_DYNAMO_MAX_STALE=PT72H`
- `APP_SESSION_TIMEOUT=PT2H`
- `APP_SESSION_COOKIE_SECURE=true`

## CI/CD (GitHub Actions)

CI runs on push/PR. Dev deploys automatically on `main` after tests pass. Prod deploys are manual via `workflow_dispatch`. Deploys update the App Runner service after pushing a new ECR image.

### Required GitHub Secrets (Dev)
- `AWS_REGION`
- `AWS_ROLE_ARN`
- `DEV_ECR_TRANSACTION_API_REPO`
- `DEV_BACKEND_SERVICE_ARN` (App Runner service ARN)
- `DEV_DATABASE_URL`
- `DEV_CORS_ALLOWED_ORIGINS`
- `DEV_ALLOWED_EMAILS` (optional)
- `DEV_ADMIN_EMAILS` (optional)
- `DEV_GOOGLE_CLIENT_ID`

### Required GitHub Secrets (Prod)
- `AWS_REGION`
- `AWS_ROLE_ARN`
- `PROD_ECR_TRANSACTION_API_REPO`
- `PROD_BACKEND_SERVICE_ARN` (App Runner service ARN)
- `PROD_DATABASE_URL`
- `PROD_CORS_ALLOWED_ORIGINS`
- `PROD_GOOGLE_CLIENT_ID`
- `PROD_ALLOWED_EMAILS` (optional)
- `PROD_ADMIN_EMAILS` (optional)

### OIDC Permissions
Role permissions for App Runner deploys must include:
- `apprunner:UpdateService`
- `apprunner:DescribeService`

## Architecture

### FX Rates (BoC -> DynamoDB -> App Runner)
- **Goal:** keep App Runner inside a VPC to reach Neon/RDS without requiring outbound internet/NAT for BoC.
- **Flow:** Lambda (outside VPC) fetches BoC/CBSA FX, writes latest CADUSD into DynamoDB.
- **App Runner:** reads the latest CAD/USD rate from DynamoDB on startup + daily schedule.
- **Local:** uses the direct BoC/CBSA HTTP call for quick debugging.
- **Why DynamoDB:** stable, low-cost, VPC-friendly via a DynamoDB gateway endpoint.

### JWT Keys (OIDC -> DynamoDB -> App Runner)
- **Goal:** let App Runner validate OIDC JWTs without outbound internet or NAT.
- **Flow:** Lambda (outside VPC) fetches provider JWKS, writes the complete JWKS document into DynamoDB.
- **App Runner:** set `APP_SECURITY_JWT_JWK_SOURCE=dynamo` and read keys through the DynamoDB gateway endpoint.
- **Staleness:** preserve the last good JWKS on Lambda fetch failure; backend rejects keys older than `APP_SECURITY_JWT_DYNAMO_MAX_STALE`.
- Full deployment checklist: `docs/private-app-runner-neon.md`.

### Performance Optimizations
- **Aggregate Stats Endpoint** (`/api/v1/trades/stats`) uses database-level aggregation with native SQL queries for O(1) memory usage
- **CAD to USD Conversion** performed in SQL queries using CASE expressions
- **Composite Indexes** on `(user_id, closed_at)` and `(user_id, currency, closed_at)` for fast aggregate queries
- **Pagination** support for large trade lists

### Database Schema
```sql
CREATE TABLE trades (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    asset_type VARCHAR(10) NOT NULL,
    direction VARCHAR(5) NOT NULL,
    quantity INTEGER NOT NULL,
    entry_price NUMERIC(12,2) NOT NULL,
    exit_price NUMERIC(12,2) NOT NULL,
    fees NUMERIC(10,2) DEFAULT 0,
    realized_pnl NUMERIC(12,2),
    opened_at DATE NOT NULL,
    closed_at DATE NOT NULL,
    option_type VARCHAR(4),
    strike_price NUMERIC(12,2),
    expiry_date DATE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Frontend Repository

The companion frontend lives at: https://github.com/alexmcdermid/tradingView
