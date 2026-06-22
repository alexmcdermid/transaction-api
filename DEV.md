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
The frontend posts the Google credential to `POST /api/v1/auth/login`. The backend validates the credential with the configured `JwtDecoder`, enforces the allowed-email list only when one is configured, creates/loads the user, and stores an `AuthenticatedUserPrincipal` in the HTTP session.

Session cookies are configured as:
- `HttpOnly`
- `Secure` by default (`APP_SESSION_COOKIE_SECURE=false` only for local HTTP)
- `SameSite=Lax` by default (`APP_SESSION_COOKIE_SAME_SITE=lax`)
- timeout from `APP_SESSION_TIMEOUT` (default `PT2H`)

Keep frontend and backend deployments same-site for browser session auth. `https://www.tradelog.ca` or `https://dev.tradelog.ca` talking to an API on another `*.tradelog.ca` hostname is same-site and works with `SameSite=Lax`. If the API is deployed on a different registrable domain, set `APP_SESSION_COOKIE_SAME_SITE=none`, keep `APP_SESSION_COOKIE_SECURE=true`, and re-check the CORS/CSRF deployment path deliberately.

CSRF protection is enabled for unsafe cookie-session requests. Browser clients should fetch `GET /api/v1/auth/csrf` and send the returned header on `POST`, `PUT`, `PATCH`, and `DELETE` requests.

Invalid, expired, or unverifiable Google credentials return `401 Invalid credential`. Dev may set `APP_SECURITY_ALLOWED_EMAILS`; valid Google accounts outside that dev allowlist return `403 Email not allowed`, and the frontend keeps the user in guest mode with the repo-owner contact message. Logout invalidates the server session and clears the session and CSRF cookies.

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
- `app.security.allowed-emails=` (empty; prod allows all Google accounts with isolated per-user data)
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
- `APP_SECURITY_ADMIN_EMAILS` (comma-separated admin allowlist)
- `APP_SECURITY_JWT_DYNAMO_MAX_STALE=PT72H`
- `APP_RATE_LIMIT_TRUST_FORWARDED_HEADERS=false`
- `APP_RATE_LIMIT_MAX_BUCKETS=10000`
- `APP_SESSION_TIMEOUT=PT2H`
- `APP_SESSION_COOKIE_SECURE=true`
- `APP_SESSION_COOKIE_SAME_SITE=lax`

### Neon JDBC URL Format

Use JDBC query params for credentials. Do not use `user:password@host` in Spring Boot configuration.

Correct JDBC shape:

```text
jdbc:postgresql://<neon-host>/<database>?user=<database-user>&password=<url-encoded-password>&sslmode=require&channel_binding=require
```

For Neon pooled hosts, keep the exact `-pooler` hostname from the Neon connection modal:

```text
jdbc:postgresql://<ep-id>-pooler.<region-host>/<database>?user=<database-user>&password=<url-encoded-password>&sslmode=require&channel_binding=require
```

Important Neon formatting details:
- Keep `channel_binding=require` exactly as generated by Neon.
- Use query params for `user` and `password`, not `postgresql://user:password@host/db`.
- URL-encode special characters in the password before putting it in the JDBC URL.
- Do not confuse AWS PrivateLink `vpce-*` IDs with Neon compute endpoint IDs.

Only if App Runner logs `Endpoint ID is not specified`, add Neon's endpoint option using the compute endpoint ID without the `-pooler` suffix:

```text
&options=endpoint%3D<ep-id>
```

If App Runner logs `password authentication failed`, App Runner reached Neon and the remaining issue is the database role/password value.

## CI/CD (GitHub Actions)

CI runs on PRs only. Direct pushes or merges to `main` do not run validation here and do not resume, deploy, or pause shared dev. The PR lifecycle deploys PR images into dev for testing after the required IAM/secrets are configured. Prod deploys are manual via `workflow_dispatch`. Deploys update App Runner only from PR dev deploy jobs or manual prod workflows after pushing a new ECR image.

Dev App Runner lifecycle is coordinated across the frontend and backend repos. A same-repository PR resumes both dev services before deploying this repo's backend image, and cleanup pauses both dev services when neither repo has an active same-repo non-draft PR that still needs shared dev.

### Dev PR Deployment Lifecycle

Shared dev is intentionally treated as a PR preview environment, not as always-on infrastructure:

1. A same-repository PR is opened, reopened, marked ready for review, or updated.
2. GitHub Actions assumes the backend AWS OIDC role through the `dev` GitHub environment.
3. The workflow resumes both dev App Runner services:
   - `DEV_BACKEND_SERVICE_ARN`
   - `DEV_FRONTEND_SERVICE_ARN`
4. The workflow builds and pushes a PR-tagged backend image:
   - `pr-<pull-request-number>-<pull-request-head-sha>`
5. The workflow updates the dev backend App Runner service to that image and runtime configuration.
6. The frontend dev service continues to call the shared dev backend through `DEV_API_BASE_URL`.
7. When a PR is closed or merged, the cleanup workflow counts active same-repo non-draft PRs in both `transaction-api` and `tradingView`.
8. If the active PR count is zero, cleanup pauses both dev App Runner services.

The frontend repo runs the same lifecycle for frontend PRs, but deploys the frontend image into the shared dev frontend service.

Important behavior:
- A backend PR resumes both services because dev testing needs both backend and frontend online.
- A frontend PR also resumes both services for the same reason.
- Cleanup pauses both services only when both repos have no active same-repo non-draft PRs. This avoids pausing shared dev while a PR in the other repo still needs it.
- `pull_request` deploys are restricted to same-repository PRs so AWS secrets are not exposed to forks.
- `pull_request_target` is used only for closed-PR cleanup, and checks out the base branch instead of the PR head.
- Neon is not scaled by this lifecycle. Only dev App Runner services are paused/resumed.
- Pausing App Runner reduces idle service cost, but it does not delete the services, ECR repos, custom domains, logs, or Neon databases.

### Required GitHub Secrets (Dev)
- `AWS_REGION`
- `AWS_ROLE_ARN`
- `DEV_ECR_TRANSACTION_API_REPO`
- `DEV_BACKEND_SERVICE_ARN` (App Runner service ARN)
- `DEV_FRONTEND_SERVICE_ARN` (needed for PR lifecycle resume/pause)
- `DEV_DATABASE_URL`
- `DEV_CORS_ALLOWED_ORIGINS`
- `DEV_ALLOWED_EMAILS` (optional)
- `DEV_ADMIN_EMAILS` (optional)
- `DEV_GOOGLE_CLIENT_ID`
- `CROSS_REPO_PR_READ_TOKEN` (optional fallback for cross-repo shared-dev cleanup)

For the current dev frontend domain:
```text
DEV_CORS_ALLOWED_ORIGINS=https://dev.tradelog.ca
```

### Required GitHub Secrets (Prod)
- `AWS_REGION`
- `AWS_ROLE_ARN`
- `PROD_ECR_TRANSACTION_API_REPO`
- `PROD_BACKEND_SERVICE_ARN` (App Runner service ARN)
- `PROD_DATABASE_URL`
- `PROD_CORS_ALLOWED_ORIGINS`
- `PROD_GOOGLE_CLIENT_ID`
- `PROD_ADMIN_EMAILS` (optional)

For the current production frontend domain:
```text
PROD_CORS_ALLOWED_ORIGINS=https://www.tradelog.ca
```

Do not set a prod allowed-email secret. Prod intentionally leaves `APP_SECURITY_ALLOWED_EMAILS` empty so all Google accounts are allowed and data isolation stays per user.

If `https://tradelog.ca` also reaches the frontend app, include it as a second allowed origin.

### OIDC Permissions
Role permissions for App Runner deploys must include:
- `apprunner:UpdateService`
- `apprunner:DescribeService`
- `apprunner:ResumeService` for PR deploys when dev is paused
- `apprunner:PauseService` for post-merge shared-dev cleanup

The GitHub Actions role for each repo must be allowed to resume and pause both shared dev App Runner services. Use name-scoped App Runner ARNs so a recreated dev service does not require updating the policy service ID:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ManageSharedDevAppRunnerLifecycle",
      "Effect": "Allow",
      "Action": [
        "apprunner:DescribeService",
        "apprunner:ResumeService",
        "apprunner:PauseService"
      ],
      "Resource": [
        "arn:aws:apprunner:<region>:<account-id>:service/<dev-frontend-service-name>/*",
        "arn:aws:apprunner:<region>:<account-id>:service/<dev-backend-service-name>/*"
      ]
    }
  ]
}
```

Keep the existing deploy permissions as well:
- `apprunner:UpdateService` for this repo's backend App Runner service.
- ECR push/read permissions for this repo's dev backend repository.
- `ecr:GetAuthorizationToken` on `*`.

If `deploy-pr-dev` fails with `AccessDeniedException` for `apprunner:ResumeService`, the role was assumed successfully and the issue is the identity policy attached to the GitHub Actions role, not the OIDC trust policy.

## Architecture

### Dev App Runner PR Lifecycle
- Goal: leave Neon running, but pause dev frontend/backend App Runner when shared dev is idle.
- PRs resume both dev App Runner services and deploy the PR image into that repo's dev service.
- Merge/close cleanup pauses both dev App Runner services only after both repos have no active same-repo non-draft PRs.
- Direct pushes or merges to `main` do not run this CI workflow or touch dev App Runner; closed-PR cleanup is responsible for pausing dev.

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
