# App Runner, Neon PrivateLink, DynamoDB Runbook

This is the current deployment model. We are deliberately keeping four App Runner services for now:

```text
dev frontend App Runner   -> public ingress, public egress, no VPC connector
dev backend App Runner    -> public ingress, public egress, public Neon connection
prod frontend App Runner  -> public ingress, public egress, no VPC connector
prod backend App Runner   -> public ingress, VPC egress, no NAT, Neon PrivateLink
```

Only the prod backend uses the private Neon path. Dev App Runner services use non-private Neon connectivity. The frontend never talks directly to Neon or DynamoDB.

```text
Browser
  -> frontend App Runner custom domain
  -> backend App Runner custom domain
  -> prod backend App Runner VPC connector
  -> Neon PrivateLink endpoint for Postgres
  -> DynamoDB gateway endpoint for FX rates and Google JWKS

EventBridge
  -> Lambda outside VPC
  -> fetch FX/JWKS over public internet
  -> write latest documents to DynamoDB
```

The backend remains publicly reachable for API traffic. The prod VPC connector only changes prod backend outbound traffic. With no NAT, prod backend runtime calls must be limited to private/VPC-reachable services such as Neon PrivateLink and DynamoDB.

## Cost Notes

Current cost model:

- Four App Runner services stay deployed: dev/prod frontend and dev/prod backend.
- Dev App Runner services use public egress and non-private Neon, so dev does not create Neon PrivateLink endpoint cost in AWS.
- Prod backend uses Neon PrivateLink and the DynamoDB gateway endpoint. Prod frontend stays public egress and does not need a VPC connector.
- There is no NAT Gateway, load balancer, API Gateway, ECS, or EKS in the current design.

Main monthly cost drivers:

- **App Runner:** usually the largest AWS baseline. You pay for provisioned memory while services are deployed, plus vCPU while services are actively processing requests. At low traffic, right-sizing CPU/memory and pausing unused dev services matter more than request volume.
- **Neon PrivateLink:** prod-only. The current low-cost layout uses three Neon interface endpoints, each in one subnet/AZ. Adding more subnets/AZs improves availability but increases endpoint ENI-hour cost.
- **Neon databases:** billed separately by Neon. All three Neon databases can still incur compute, storage, history/restore, branch, and transfer costs. Dev being non-private only removes AWS PrivateLink cost; it does not remove Neon compute/storage cost.
- **DynamoDB:** the shared `ExchangeRates` and `AuthJwks` tables should stay near-zero for this app. The DynamoDB gateway endpoint itself has no additional charge.
- **Lambda and EventBridge:** the JWKS and FX scheduled jobs should be free or near-free at this schedule and payload size.
- **ECR:** private image storage is cheap but grows if old images are retained. Same-region pulls from ECR to App Runner do not add transfer cost.
- **CloudWatch Logs:** log ingestion/storage can creep if debug logging is left on or retention is unlimited.
- **DNS:** App Runner custom domains and ACM certificates do not add a separate App Runner charge. Route 53 only matters if DNS is hosted there; otherwise DNS cost sits with the external DNS provider.

Cost levers:

1. Pause dev App Runner services when they are not needed, and resume/deploy them for PR testing.
2. Use the smallest App Runner CPU/memory shape that runs frontend and backend comfortably.
3. Keep Neon PrivateLink prod-only.
4. Keep one subnet/AZ per Neon endpoint until availability matters more than endpoint cost.
5. Add ECR lifecycle policies for old images.
6. Set CloudWatch log retention.
7. Keep NAT Gateway out of this design.

For the budget alert and App Runner auto-pause guardrail, see [AWS Budget Guardrail Runbook](aws-budget-guardrail.md).

Neon is intentionally excluded from the PR-driven dev App Runner resume, PR deployment, and pause lifecycle.

## Dev App Runner PR Lifecycle

Shared dev App Runner is intentionally not always-on. Dev frontend and backend services are kept deployed, but GitHub Actions resumes them only when active development needs the shared dev environment.

The lifecycle is shared across both repositories:

```text
frontend PR opened/updated
  -> resume dev backend + dev frontend
  -> build/push frontend PR image
  -> update dev frontend App Runner

backend PR opened/updated
  -> resume dev backend + dev frontend
  -> build/push backend PR image
  -> update dev backend App Runner

PR closed or merged in either repo
  -> count open PRs across frontend + backend repos
  -> if count is zero, pause dev frontend + dev backend
```

### Why One PR Resumes Both Services

Shared dev needs both services online for realistic testing:

- A frontend PR still needs the dev backend API.
- A backend PR still needs the dev frontend for browser testing.
- Pausing only one service creates misleading failures and makes the dev URL look broken.

Therefore each repo's PR deploy resumes both App Runner services before deploying that repo's image.

### Workflow Events

The normal CI workflow handles PR deploys:

```text
pull_request: opened, synchronize, reopened, ready_for_review
```

Those jobs run only for same-repository PRs:

```text
github.event.pull_request.head.repo.full_name == github.repository
```

This keeps AWS deployment secrets away from forked PRs.

Closed-PR cleanup is handled by a separate workflow:

```text
pull_request_target: closed
```

That workflow checks out the base branch, not the PR head, then counts open PRs across both repos. It only pauses dev when the cross-repo open PR count is zero.

Direct pushes or merges to `main` do not resume, deploy, or pause shared dev. After a PR merge, the closed-PR cleanup workflow is the path that returns dev App Runner to the paused state when no open PRs remain.

### What Is And Is Not Scaled

Scaled by this lifecycle:

- Dev frontend App Runner service.
- Dev backend App Runner service.

Not scaled by this lifecycle:

- Neon databases.
- Prod App Runner services.
- ECR repositories or images.
- App Runner custom domains.
- CloudWatch log groups.
- DynamoDB tables.
- Neon PrivateLink endpoints.

Pausing App Runner reduces idle App Runner service cost. It does not tear down infrastructure and does not remove persistent data.

### Required GitHub Secrets

Both repos need the opposite service ARN because each repo resumes and pauses both dev services:

Frontend repo:

```text
DEV_FRONTEND_SERVICE_ARN=<dev-frontend-app-runner-service-arn>
DEV_BACKEND_SERVICE_ARN=<dev-backend-app-runner-service-arn>
```

Backend repo:

```text
DEV_BACKEND_SERVICE_ARN=<dev-backend-app-runner-service-arn>
DEV_FRONTEND_SERVICE_ARN=<dev-frontend-app-runner-service-arn>
```

`CROSS_REPO_PR_READ_TOKEN` is optional. If present, workflows use it to count open PRs across both repos. If omitted, workflows fall back to `github.token`, which may be insufficient for cross-repo visibility depending on repository permissions.

### Required IAM Permissions

Each repo's GitHub Actions role must be able to manage the lifecycle for both shared dev App Runner services:

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

Use service-name wildcards for the generated service ID segment. The permission remains scoped to the two dev services, but does not break if a dev App Runner service is recreated.

Keep each repo's existing deploy permissions:

- Frontend role: `apprunner:UpdateService` for the dev frontend service and ECR push/read access for the frontend ECR repo.
- Backend role: `apprunner:UpdateService` for the dev backend service and ECR push/read access for the backend ECR repo.
- Both roles: `ecr:GetAuthorizationToken` on `*`.

If GitHub Actions logs say `AccessDeniedException` for `apprunner:ResumeService` or `apprunner:PauseService`, the role has already been assumed. Fix the identity policy attached to the role; do not start with OIDC trust debugging.

### Manual Operations

Manual resume:

```bash
aws apprunner resume-service --service-arn "<dev-backend-app-runner-service-arn>"
aws apprunner resume-service --service-arn "<dev-frontend-app-runner-service-arn>"
```

Manual pause:

```bash
aws apprunner pause-service --service-arn "<dev-frontend-app-runner-service-arn>"
aws apprunner pause-service --service-arn "<dev-backend-app-runner-service-arn>"
```

Check status:

```bash
aws apprunner describe-service \
  --service-arn "<dev-backend-app-runner-service-arn>" \
  --query "Service.Status" \
  --output text

aws apprunner describe-service \
  --service-arn "<dev-frontend-app-runner-service-arn>" \
  --query "Service.Status" \
  --output text
```

## Deployment Identifiers

Do not commit concrete AWS account IDs, VPC IDs, subnet IDs, route table IDs, VPC endpoint IDs, security group IDs, App Runner generated hostnames, Neon organization IDs, Neon project IDs, or production DNS targets.

Keep those values in the deployment environment, AWS console, Neon console, password manager, or an untracked local operator note. This committed runbook intentionally uses placeholders.

## Repo Scope

Implemented in this PR:

- Backend supports `app.security.jwt.jwk-source=dynamo`.
- Backend reads Google JWKS from DynamoDB and refreshes its local verifier cache.
- Backend DynamoDB config supports both FX and JWKS reads.
- Backend GitHub workflows deploy with Dynamo-backed JWKS instead of inline JWKS.

Not included:

- Combining frontend and backend into one container.
- Combining frontend and backend into one App Runner per environment.

## Shared Auth Model

The app uses a first-party session model:

- The browser talks to Google directly.
- The frontend sends the Google credential once to `POST /api/v1/auth/login`.
- The backend validates the credential locally using Google JWKS mirrored into DynamoDB.
- The backend stores the authenticated principal in an `HttpOnly`, `Secure`, `SameSite=Lax` session cookie.
- Browser unsafe methods use the CSRF token from `GET /api/v1/auth/csrf`.

For this small project, shared auth metadata is acceptable:

- Use one Google OAuth web client ID for local/dev/prod.
- Add all frontend origins to that OAuth client:
  - `http://localhost:5173`
  - `<dev-frontend-origin>`
  - `<prod-frontend-origin>`
  - `<prod-root-origin-if-forwarded-or-supported>`
- Use the same Google client ID as backend `APP_SECURITY_JWT_AUDIENCE`.
- Leave `APP_SECURITY_ALLOWED_EMAILS` empty if any Google account can sign up and use isolated per-user data.
- Keep `APP_SECURITY_ADMIN_EMAILS` restricted.

## GitHub Secrets

The `*_ECR_*_REPO` values are ECR repository names, not full ECR URIs.

### Frontend Repo: `tradingView`

Prod environment secrets:

```text
AWS_REGION=<aws-region>
AWS_ROLE_ARN=arn:aws:iam::<account-id>:role/<frontend-github-actions-role-name>
PROD_ECR_TRADINGVIEW_REPO=<prod-frontend-ecr-repo-name>
PROD_FRONTEND_SERVICE_ARN=arn:aws:apprunner:<region>:<account-id>:service/<service-name>/<service-id>
PROD_API_BASE_URL=<prod-api-origin>/api/v1
PROD_GOOGLE_CLIENT_ID=<google-web-client-id>
PROD_ADMIN_EMAILS=<comma-separated-admin-emails>
PROD_PUBLIC_ORIGIN=<prod-frontend-origin>
PROD_PUBLIC_HOST_ALLOWLIST=<prod-frontend-origin-host>,<prod-root-origin-host-if-forwarded-or-supported>
```

Dev environment secrets use the same names with `DEV_`.

Current frontend values:

```text
DEV_PUBLIC_ORIGIN=https://dev.tradelog.ca
DEV_PUBLIC_HOST_ALLOWLIST=dev.tradelog.ca

PROD_PUBLIC_ORIGIN=https://www.tradelog.ca
PROD_PUBLIC_HOST_ALLOWLIST=www.tradelog.ca
```

If `https://tradelog.ca` reaches the React SSR app instead of redirecting before the app, add `tradelog.ca` to `PROD_PUBLIC_HOST_ALLOWLIST`.

### Backend Repo: `transaction-api`

Prod environment secrets:

```text
AWS_REGION=<aws-region>
AWS_ROLE_ARN=arn:aws:iam::<account-id>:role/<backend-github-actions-role-name>
PROD_ECR_TRANSACTION_API_REPO=<prod-backend-ecr-repo-name>
PROD_BACKEND_SERVICE_ARN=arn:aws:apprunner:<region>:<account-id>:service/<service-name>/<service-id>
PROD_DATABASE_URL=<jdbc-neon-url>
PROD_CORS_ALLOWED_ORIGINS=<prod-frontend-origin>,<prod-root-origin-if-forwarded-or-supported>
PROD_GOOGLE_CLIENT_ID=<google-web-client-id>
PROD_ADMIN_EMAILS=<comma-separated-admin-emails>
```

Do not set `PROD_ALLOWED_EMAILS`. Prod intentionally leaves `APP_SECURITY_ALLOWED_EMAILS` empty so all Google accounts are allowed and data isolation stays per user. Keep the allowed-email gate for dev only.

Current backend CORS values:

```text
DEV_CORS_ALLOWED_ORIGINS=https://dev.tradelog.ca
PROD_CORS_ALLOWED_ORIGINS=https://www.tradelog.ca
```

If `https://tradelog.ca` reaches the frontend app, add `https://tradelog.ca` to `PROD_CORS_ALLOWED_ORIGINS`.

## Neon JDBC URL Format

Use JDBC query params for credentials. Do not use `user:password@host`.

Correct:

```text
jdbc:postgresql://<host>:5432/<db>?user=<user>&password=<url-encoded-password>&sslmode=require&channel_binding=require
```

Example:

```text
jdbc:postgresql://<neon-host>:5432/<database>?user=<database-user>&password=<encoded-password>&sslmode=require&channel_binding=require
```

For pooled Neon hosts, keep the exact `-pooler` hostname from the Neon connection modal:

```text
jdbc:postgresql://<ep-id>-pooler.<region-host>/<database>?user=<database-user>&password=<encoded-password>&sslmode=require&channel_binding=require
```

Wrong for this app:

```text
jdbc:postgresql://user:password@host/db?sslmode=require
```

If the password contains special characters, URL-encode them:

```text
@ -> %40
: -> %3A
/ -> %2F
? -> %3F
& -> %26
# -> %23
% -> %25
```

Important details:

- Keep `channel_binding=require` exactly as generated by Neon.
- AWS PrivateLink `vpce-*` IDs are not Neon compute endpoint IDs and never belong in `DATABASE_URL`.
- Only if App Runner logs `Endpoint ID is not specified`, add Neon's endpoint option using the compute endpoint ID without the `-pooler` suffix: `&options=endpoint%3D<ep-id>`.
- `password authentication failed` means the network and endpoint routing reached Neon, and the database role/password value is wrong or stale.

## GitHub OIDC Roles

The workflows use GitHub environments, so the OIDC subject must allow `environment:prod`, not only `ref:refs/heads/main`.

Backend role trust policy should include:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<account-id>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": [
            "repo:<github-owner>/<backend-repo>:ref:refs/heads/main",
            "repo:<github-owner>/<backend-repo>:environment:dev",
            "repo:<github-owner>/<backend-repo>:environment:prod"
          ]
        }
      }
    }
  ]
}
```

Frontend role trust policy should include:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<account-id>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": [
            "repo:<github-owner>/<frontend-repo>:ref:refs/heads/main",
            "repo:<github-owner>/<frontend-repo>:environment:dev",
            "repo:<github-owner>/<frontend-repo>:environment:prod"
          ]
        }
      }
    }
  ]
}
```

Deploy role permissions need:

- `ecr:GetAuthorizationToken` on `*`
- ECR push/read actions on the relevant ECR repos
- `apprunner:UpdateService`
- `apprunner:DescribeService`
- `apprunner:ResumeService` on both dev App Runner services
- `apprunner:PauseService` on both dev App Runner services

## Bootstrap Order For New Prod Services

App Runner can only be created from an existing ECR image, but the deploy workflow needs an App Runner service ARN to fully complete.

Use this order:

1. Create prod ECR repos:
   - `<prod-backend-ecr-repo-name>`
   - `<prod-frontend-ecr-repo-name>`
2. Set bootstrap GitHub secrets:
   - `AWS_REGION`
   - `AWS_ROLE_ARN`
   - `PROD_ECR_*_REPO`
   - frontend also needs `PROD_API_BASE_URL`, `PROD_GOOGLE_CLIENT_ID`, and `PROD_PUBLIC_ORIGIN`
3. Run prod deploy workflow once.
4. It should build and push the ECR image.
5. If `PROD_*_SERVICE_ARN` is missing, the workflow will fail after the image push. That is expected.
6. Create the App Runner service from the pushed ECR image.
7. Copy the App Runner service ARN into GitHub secrets.
8. Run prod deploy again.

If GitHub fails at `Could not assume role with OIDC`, fix the role trust policy before continuing. No ECR image is pushed until credentials are assumed.

## App Runner Services

### Frontend

Service names:

- `<dev-frontend-app-runner-service-name>`
- `<prod-frontend-app-runner-service-name>`

Configuration:

- Source: ECR image.
- Port: `3000`.
- Incoming traffic: public.
- Outgoing traffic: default public networking.
- No VPC connector required.

Build args:

```text
VITE_API_BASE_URL=<prod-api-origin>/api/v1
VITE_GOOGLE_CLIENT_ID=<google-web-client-id>
VITE_ADMIN_EMAILS=<comma-separated-admin-emails>
VITE_USE_HEADER_AUTH=false
VITE_USER_ID=
VITE_PUBLIC_ORIGIN=<prod-frontend-origin>
VITE_PUBLIC_HOST_ALLOWLIST=<prod-frontend-origin-host>,<prod-root-origin-host-if-forwarded-or-supported>
```

### Backend

Service names:

- `<dev-backend-app-runner-service-name>`
- `<prod-backend-app-runner-service-name>`

Configuration:

- Source: ECR image.
- Port: `8080`.
- Incoming traffic: public.
- Dev outgoing traffic: default public networking, using a non-private Neon connection.
- Prod outgoing traffic: custom VPC connector, using Neon PrivateLink.
- Prod VPC connector: private subnets and `<backend-egress-security-group-name>`.
- Prod has no NAT Gateway.
- Instance role: DynamoDB read role.

Do not set incoming traffic to private for the backend API unless an API Gateway/private client design is introduced. The frontend needs to reach the backend API over the public App Runner/custom domain.

Runtime env:

```text
DATABASE_URL=<jdbc-neon-url>
DB_MAX_POOL_SIZE=3
DB_MIN_IDLE=0

APP_AWS_REGION=<aws-region>
APP_CORS_ALLOWED_ORIGINS=<prod-frontend-origin>,<prod-root-origin-if-forwarded-or-supported>

APP_FX_SOURCE=dynamo
APP_FX_DYNAMO_TABLE=ExchangeRates

APP_SECURITY_JWT_ENABLED=true
APP_SECURITY_ALLOW_HEADER_AUTH=false
APP_SECURITY_JWT_ISSUER_URI=https://accounts.google.com
APP_SECURITY_JWT_AUDIENCE=<google-web-client-id>
APP_SECURITY_JWT_JWK_SOURCE=dynamo
APP_SECURITY_JWT_JWS_ALGORITHMS=RS256
APP_SECURITY_JWT_JWK_REFRESH_INTERVAL=PT15M
APP_SECURITY_JWT_DYNAMO_TABLE=AuthJwks
APP_SECURITY_JWT_DYNAMO_KEY_ATTRIBUTE=provider
APP_SECURITY_JWT_DYNAMO_KEY=google
APP_SECURITY_JWT_DYNAMO_JWK_SET_ATTRIBUTE=jwks
APP_SECURITY_JWT_DYNAMO_EXPIRES_AT_ATTRIBUTE=expiresAt
APP_SECURITY_JWT_DYNAMO_MAX_STALE=PT72H
APP_SESSION_TIMEOUT=PT2H
APP_SESSION_COOKIE_SECURE=true
APP_SESSION_COOKIE_SAME_SITE=lax

APP_SECURITY_ALLOWED_EMAILS=
APP_SECURITY_ADMIN_EMAILS=<comma-separated-admin-emails>
```

Keep the frontend and API under the same registrable domain for the browser session flow. For example, `www.tradelog.ca` or `dev.tradelog.ca` calling an API on another `*.tradelog.ca` hostname is same-site and works with `SameSite=Lax`. If the API lives on a different registrable domain, use `APP_SESSION_COOKIE_SAME_SITE=none` with `APP_SESSION_COOKIE_SECURE=true` and retest login plus unsafe API calls end-to-end.

The frontend never stores the Google credential or bearer token in local storage. It exchanges the credential once at `/api/v1/auth/login`, then uses `credentials: include` with the first-party session cookie. Unsafe browser requests must include the CSRF header returned by `/api/v1/auth/csrf`; frontend code caches that token and clears it on logout.

## Public DNS And Custom Domains

Current DNS provider: `<dns-provider>`.

Current public domain model:

```text
<prod-root-origin>      -> DNS provider 301 forwarding -> <prod-frontend-origin>
<prod-frontend-origin>  -> App Runner prod frontend
<prod-api-origin>       -> App Runner prod backend
<dev-frontend-origin>   -> App Runner dev frontend
<dev-api-origin>        -> App Runner dev backend
```

DNS records that matter:

```text
A      @                <dns-provider-forwarding-ip-1>
A      @                <dns-provider-forwarding-ip-2>
CNAME  <prod-frontend> <prod-frontend-apprunner-hostname>
CNAME  <prod-api>      <prod-backend-apprunner-hostname>
CNAME  <dev-frontend>  <dev-frontend-apprunner-hostname>
CNAME  <dev-api>       <dev-backend-apprunner-hostname>
```

If using DNS-provider forwarding, the `A @` records are forwarding infrastructure. They do not point directly to App Runner.

Keep all AWS ACM validation CNAMEs created by App Runner. They are required for certificate validation and renewal.

The frontend App Runner service must have the canonical frontend hostname linked as an active custom domain, not only the root hostname. A DNS `CNAME <frontend-host> -> <service-generated-hostname>` is not enough by itself because App Runner still needs to issue/bind a certificate for that exact hostname. If the canonical frontend hostname is missing from App Runner custom domains, browsers will fail TLS even though DNS points at the right service.

DNS-provider forwarding configuration:

```text
From: <prod-root-origin>
To: <prod-frontend-origin>
Type: Permanent 301
Mode: Forward only
```

Do not use forwarding with masking. Masking frames the app and can break auth, routing, redirects, cookies, and browser APIs.

### Why Root Domain Is Special

The root domain is the DNS apex. The apex must already have `NS` and `SOA` records, so it cannot be a normal CNAME. App Runner gives a hostname, not stable IPs, so an ordinary `A @` record cannot directly target App Runner.

Subdomains can use normal CNAMEs:

```text
<prod-frontend-host> -> App Runner
<prod-api-host> -> App Runner
```

The root domain needs one of these:

- DNS provider apex aliasing, such as Route 53 Alias, Cloudflare CNAME flattening, or an ALIAS/ANAME feature.
- HTTP forwarding from root to `www`.
- A reverse proxy/CDN with stable apex support in front of App Runner.

### Future Cleaner Options

Option 1: move DNS hosting to Route 53 while keeping the domain registered at the current registrar.

- Create a Route 53 public hosted zone for `<prod-domain>`.
- Copy all existing DNS records into Route 53.
- Create a Route 53 `A`/`AAAA` alias for `<prod-root-host>` to the App Runner frontend service.
- Keep frontend/API/dev records.
- Change registrar nameservers to the four Route 53 nameservers.

This lets the browser stay on `<prod-root-origin>` without forwarding.

Option 2: move DNS hosting to Cloudflare.

- Use Cloudflare CNAME flattening for `<prod-root-host>`.
- Keep frontend/API/dev records as DNS-only unless intentionally proxying.
- Be careful if proxying App Runner traffic through Cloudflare; test Host headers, TLS mode, OAuth redirects, and websocket/API behavior.

Option 3: keep DNS-provider forwarding.

- Lowest effort.
- Browser canonical URL becomes `<prod-frontend-origin>`.
- App and OAuth config should include both `<prod-frontend-origin>` and `<prod-root-origin>`, but treat the forwarded frontend origin as canonical.

## DynamoDB

These tables can be shared by dev and prod in the same AWS account/region.

### `ExchangeRates`

Partition key:

```text
pair string
```

CAD/USD item:

```json
{
  "pair": "CADUSD",
  "rate": "0.732",
  "effectiveDate": "2026-05-15"
}
```

### `AuthJwks`

Partition key:

```text
provider string
```

Google item:

```json
{
  "provider": "google",
  "issuer": "https://accounts.google.com",
  "jwks": "{\"keys\":[...]}",
  "fetchedAt": "2026-05-15T15:00:00Z",
  "expiresAt": "2026-05-15T21:00:00Z"
}
```

`jwks` must be a DynamoDB string containing the complete JSON JWKS document. The backend reads `provider`, `jwks`, and `expiresAt`.

## Lambda Jobs

Run Lambdas outside the VPC unless they need private resources. Outside-VPC Lambdas can call public internet endpoints without NAT.

One JWKS Lambda and one FX Lambda can serve both dev and prod.

### Google JWKS Lambda

- Trigger: EventBridge schedule every 1 to 6 hours.
- Fetch Google JWKS from `https://www.googleapis.com/oauth2/v3/certs`.
- Preserve the previous DynamoDB item if fetch fails.
- Parse `Cache-Control: max-age=<seconds>` when present.
- Set `expiresAt` from cache max-age. If missing, use a conservative default such as 6 hours.
- Write the full JWKS JSON to `AuthJwks`.
- Do not filter keys down to one `kid`.

### FX Lambda

- Keep the existing BoC/CBSA flow.
- Write latest CAD/USD quote to `ExchangeRates`.
- Preserve the previous good quote on fetch failure.

## IAM Runtime Roles

### Backend App Runner Instance Role

Trust entity:

```text
tasks.apprunner.amazonaws.com
```

Policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["dynamodb:GetItem"],
      "Resource": [
        "arn:aws:dynamodb:<region>:<account-id>:table/AuthJwks",
        "arn:aws:dynamodb:<region>:<account-id>:table/ExchangeRates"
      ]
    }
  ]
}
```

### JWKS Lambda Role

Policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem"],
      "Resource": "arn:aws:dynamodb:<region>:<account-id>:table/AuthJwks"
    }
  ]
}
```

## VPC And Neon PrivateLink

Use the same AWS region for:

- Prod backend App Runner service.
- Prod backend App Runner VPC connector.
- VPC endpoints.
- Neon project.

### VPC Baseline

For a no-NAT backend, the VPC must still have DNS support:

```text
DNS resolution: Enabled
DNS hostnames: Enabled
```

In the AWS console:

1. VPC -> Your VPCs.
2. Select `<prod-vpc-name>`.
3. Actions -> Edit VPC settings.
4. Enable DNS hostnames.
5. Save.

If DNS hostnames are disabled, App Runner can start but database startup can hang/fail because the Neon hostname does not resolve through the PrivateLink path as expected.

The prod private route table should have:

```text
<prod-vpc-cidr> -> local
com.amazonaws.<aws-region>.dynamodb prefix list -> <dynamodb-gateway-endpoint-name>
```

Do not add an internet gateway or NAT route to this private route table for the no-NAT design.

### VPC Endpoint Setup

Create a security group:

```text
<backend-egress-security-group-name>
```

Attach it to the prod backend App Runner VPC connector.

Outbound rule:

```text
Type: All traffic
Destination: 0.0.0.0/0
```

This is acceptable because the selected private subnets have no NAT/IGW route. The route table still limits reachable destinations to local VPC resources and configured VPC endpoints.

Create another security group:

```text
neon-privatelink-endpoint-sg
```

Inbound rule:

```text
Type: PostgreSQL
Port: 5432
Source: <backend-egress-security-group-name>
```

Required VPC endpoints:

- DynamoDB gateway endpoint attached to private subnet route tables.
- Neon interface endpoint(s) for the Neon service names in your region.

Some Neon regions require one VPC endpoint for each service name. Copy the current service names from Neon Private Networking docs or the Neon console for the target region:

```text
<neon-privatelink-service-name-1>
<neon-privatelink-service-name-2-if-required>
<neon-privatelink-service-name-3-if-required>
```

When creating Neon endpoints in AWS:

1. AWS Console -> VPC -> Endpoints -> Create endpoint.
2. Choose endpoint services that use NLBs/GWLBs.
3. Paste Neon service name.
4. Verify service.
5. Select the same VPC as the backend App Runner connector.
6. Select private subnets.
7. Attach `neon-privatelink-endpoint-sg`.
8. Do not enable private DNS yet.
9. Create endpoint.
10. Copy each generated `vpce-*` endpoint ID.

After endpoint creation, verify each Neon endpoint uses:

```text
VPC: <prod-vpc-name>
Subnets: the selected prod private subnet(s)
Security group: neon-privatelink-endpoint-sg
Status: Available
```

If the endpoints show `Pending` after a modification, wait for them to return to `Available` before retrying App Runner. If they remain `Pending` for more than 10-15 minutes, re-run the Neon endpoint assignment commands.

### Temporary Low-Cost Neon Endpoint Layout

Current setup:

- Keep all three Neon PrivateLink endpoint service mappings required for the AWS region.
- Provision each Neon interface endpoint in one shared private subnet/AZ for now to reduce endpoint cost.
- Keep the backend App Runner VPC connector using that same subnet while this low-cost layout is active.
- Keep the DynamoDB gateway endpoint as-is.

This is a cost reduction, not a security change. The database remains private through Neon PrivateLink, with the same authentication and TLS behavior. The tradeoff is availability: if the selected AZ/subnet has an issue, App Runner -> Neon private DB connectivity can fail until the AZ recovers or additional subnets are added back.

To switch back to the higher-availability setup:

1. AWS Console -> VPC -> Endpoints.
2. Open each Neon interface endpoint.
3. Modify the endpoint subnets.
4. Re-enable the other private subnets/AZs for each Neon endpoint.
5. Wait until all Neon endpoints return to `Available`.
6. Confirm the App Runner VPC connector includes the same private subnets/AZs.

### Assign Endpoints In Neon

Neon wants VPC endpoint IDs, not the VPC ID. The examples below use the `neon` CLI command shown in Neon docs; if the local install is exposed as `neonctl`, use the same subcommands with `neonctl`.

Install/auth Neon CLI:

```bash
neon auth
neon orgs list
neon projects list
```

Assign each endpoint to the Neon organization:

```bash
neon vpc endpoint assign vpce-xxxxxxxx \
  --org-id <neon-org-id> \
  --region-id <neon-region-id>
```

After Neon accepts the endpoints:

1. AWS Console -> VPC -> Endpoints.
2. Select each Neon endpoint.
3. Actions -> Modify private DNS name.
4. Enable private DNS.

Enabling private DNS can temporarily move the endpoint back to `Pending`. Wait until all three Neon endpoints are `Available` again before updating App Runner.

The Neon connection string does not change. Inside the VPC, private DNS should resolve the same Neon hostname to private endpoint IPs.

Only after testing should you restrict/block public Neon access.

Neon's "Allow traffic via Virtual Private Network (VPC)" project setting is project-scoped. Enabling it on the prod project does not break other Neon projects unless those apps also use the same project. If old apps share the same Neon project, project-level restrictions can affect them.

To restrict a Neon project to specific VPC endpoints through the CLI, apply each endpoint ID to the project:

```bash
neon vpc project restrict vpce-xxxxxxxx --project-id <neon-project-id>
```

If the region uses multiple service names, restrict the project to all endpoint IDs for that region. After the private path is verified, public internet access can be blocked:

```bash
neon projects update <neon-project-id> --block-public-connections true
```

### App Runner VPC Connector

Backend App Runner networking:

```text
Incoming network traffic: Public endpoint
Endpoint IP address type: IPv4
Outgoing network traffic: Custom VPC
VPC connector: transaction-api prod connector
```

The connector must use:

```text
VPC: <prod-vpc-name> / <prod-vpc-id>
Subnets: the prod private subnet(s) associated with <prod-private-route-table-name>
Security group: <backend-egress-security-group-name> / <backend-egress-security-group-id>
```

While the temporary low-cost Neon layout is active, include the same private subnet/AZ that contains the Neon endpoint ENIs.

If App Runner rolls back to public egress after saving the VPC connector, treat that as an app startup failure. App Runner applies the network change, the container fails health/startup, and App Runner restores the previous config. Look at the failed deployment logs.

The common failure signature for DB networking is:

```text
HikariPool-1 - Starting...
...
Error creating bean with name 'userRepository'
Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'
```

Scroll to the bottom-most `Caused by:` in the log. It should identify the real issue, such as `Connection timed out`, `UnknownHostException`, `Connection refused`, or authentication failure.

## Verification

Before blocking public Neon access:

- Backend App Runner deploy succeeds.
- Backend health endpoint returns `UP`.
- `<prod-vpc-name>` has DNS resolution and DNS hostnames enabled.
- All three Neon interface endpoints are `Available`.
- All three Neon interface endpoints have private DNS enabled.
- Temporary low-cost layout only: all three Neon interface endpoints use the same selected private subnet/AZ, and the App Runner VPC connector includes that subnet.
- JWKS Lambda writes `AuthJwks/provider=google`.
- FX Lambda writes `ExchangeRates/pair=CADUSD`.
- Backend App Runner instance role can read both DynamoDB items.
- DynamoDB gateway endpoint is attached to private subnet route tables.
- Neon endpoint SG allows `5432` from backend App Runner SG.
- App Runner VPC connector uses the prod private subnets and `<backend-egress-security-group-name>`.
- Backend can connect to Neon with the JDBC URL.
- Sign-in works through the frontend.
- Bad Google credentials return `401 Invalid credential`.
- Dev only: valid Google accounts outside `APP_SECURITY_ALLOWED_EMAILS` return `403 Email not allowed` and remain in frontend guest mode with an allowlist contact message.
- Prod has `APP_SECURITY_ALLOWED_EMAILS` empty, so any valid Google account can sign in to isolated per-user data.
- Logout clears the session; signing back in creates a fresh session.
- API calls work for a normal Google account.
- Admin endpoints work only for `APP_SECURITY_ADMIN_EMAILS`.

Failure modes:

- `Could not assume role with OIDC`: GitHub role trust policy does not allow the repo/environment subject.
- `Missing required env var: APP_SECURITY_JWT_AUDIENCE`: missing `PROD_GOOGLE_CLIENT_ID` in backend repo environment secrets.
- `JDBC URL invalid port number`: credentials are in `user:password@host`; move them to `?user=...&password=...`.
- `JWKS item not found in DynamoDB`: `AuthJwks` item is missing, wrong partition key, or App Runner role lacks `GetItem`.
- `JWKS item is stale`: Lambda has not refreshed keys inside `APP_SECURITY_JWT_DYNAMO_MAX_STALE`.
- DB connection timeout: PrivateLink endpoint, private DNS, security group, route table, or Neon endpoint assignment is incomplete.
- App Runner update rolls back to public egress: the new VPC config caused app startup or health checks to fail. Check failed deployment logs, Neon endpoint status, private DNS, VPC DNS hostnames, SGs, and the bottom-most DB exception.

## Dev To Prod Data Promotion

Using the current dev database as the initial prod database is acceptable for a small project if it is a one-time seed, not continuous replication.

Before importing dev into prod:

- Keep a prod backup/restore point.
- Ensure prod leaves `APP_SECURITY_ALLOWED_EMAILS` empty for public signup.
- Prune obvious test users, blocked users, stale share links, and junk data.
- Use the same Google OAuth client ID if you want existing `authId` values to remain stable.
- After import, keep dev and prod databases separate.
