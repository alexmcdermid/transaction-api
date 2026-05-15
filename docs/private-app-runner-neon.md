# Private App Runner + Neon Deployment

Phase 1 target architecture for the current two-repo/two-service setup:

```text
Browser
  -> public frontend App Runner custom domain
  -> frontend App Runner service, no VPC connector required
  -> public backend App Runner custom domain
  -> backend App Runner service
  -> backend App Runner VPC connector, no NAT
  -> Neon PrivateLink endpoint for Postgres
  -> DynamoDB gateway endpoint for FX rates and provider JWKS

EventBridge
  -> Lambda outside VPC
  -> fetch FX/JWKS over public internet
  -> write latest documents to DynamoDB
```

Both App Runner services remain publicly reachable. The VPC connector is only for backend outbound application traffic. The frontend does not need private networking because it never connects to Neon or DynamoDB directly.

This document keeps the current Google OAuth frontend model. The browser still talks to Google directly, and the backend validates Google ID tokens using the JWKS document mirrored into DynamoDB. A later first-party session/JWT layer would reduce provider-token usage on every API call, but it is not required for the no-NAT deployment.

For this small project, shared auth metadata is acceptable:

- Use one Google OAuth client ID for local/dev/prod.
- Add each frontend origin to that OAuth client, for example `http://localhost:5173`, `https://dev.tradelog.ca`, and `https://tradelog.ca`.
- Use the same `APP_SECURITY_JWT_AUDIENCE` in dev and prod.
- Leave `APP_SECURITY_ALLOWED_EMAILS` empty if any Google account should be allowed to create and use its own isolated data.
- Keep `APP_SECURITY_ADMIN_EMAILS` environment-specific.
- Sharing JWKS and FX DynamoDB tables across dev/prod is fine because those documents are provider/public market metadata, not user data or secrets.

## This PR Scope

Done:

- Backend supports `app.security.jwt.jwk-source=dynamo`.
- Backend can read provider JWKS from DynamoDB and refresh its local verifier cache.
- Existing DynamoDB client config now supports both FX and JWKS readers.

Not included in this PR:

- Add a combined container image that runs the frontend SSR server, backend API, and one reverse proxy.
- Route `/api/v1/*` to Spring Boot and all other paths to the React Router server.
- Build frontend with `VITE_API_BASE_URL=/api/v1`.
- If React Router loaders need server-side API calls, add a server-only internal API base such as `http://127.0.0.1:8080/api/v1`.
- Update CI to build/push one image per environment instead of separate frontend/backend images.

The single-service App Runner consolidation can happen later. This phase keeps the existing frontend and backend repositories and CI/CD pipelines.

## AWS Resources

Create one set per environment unless noted otherwise. DynamoDB tables and the scheduled Lambda jobs can be shared by dev/prod.

### 1. VPC

Use private subnets across at least two AZs.

No NAT Gateway is required for this design.

Required endpoints:

- **DynamoDB gateway endpoint** attached to the private subnet route tables.
- **Neon PrivateLink interface endpoint(s)** in the same VPC and AWS region as the App Runner VPC connector.

Security groups:

- `backend-apprunner-egress-sg`: attach to the backend App Runner VPC connector.
- `neon-privatelink-endpoint-sg`: attach to the Neon interface endpoint.
- Allow inbound TCP `5432` on `neon-privatelink-endpoint-sg` from `backend-apprunner-egress-sg`.
- Keep App Runner inbound public through App Runner. Do not configure App Runner private ingress unless you want the app reachable only from inside the VPC.

### 2. Neon Private Networking

In Neon:

- Enable Private Networking for the project.
- Assign the AWS VPC endpoint ID(s) to the Neon organization.
- Enable private DNS on the AWS endpoint after Neon accepts the endpoint.
- Restrict public internet access to the Neon project after the private path is verified.

The Neon database connection string does not change. Inside the VPC, private DNS should resolve the same Neon hostname to private endpoint IPs.

Use separate Neon projects for dev/prod if possible. Separate branches are cheaper, but separate projects give cleaner blast-radius and public-access controls.

### 3. DynamoDB Tables

These tables can be shared by dev and prod if both App Runner services run in the same AWS account/region.

Existing FX table:

- Table: `ExchangeRates`
- Partition key: `pair` string
- CAD/USD item:

```json
{
  "pair": "CADUSD",
  "rate": "0.732",
  "effectiveDate": "2026-05-15"
}
```

New JWKS table:

- Table: `AuthJwks`
- Partition key: `provider` string
- Google item:

```json
{
  "provider": "google",
  "issuer": "https://accounts.google.com",
  "jwks": "{\"keys\":[...]}",
  "fetchedAt": "2026-05-15T15:00:00Z",
  "expiresAt": "2026-05-15T21:00:00Z",
  "etag": "\"optional-provider-etag\""
}
```

The backend currently reads only `provider`, `jwks`, and `expiresAt`. The extra fields are for operator visibility and Lambda idempotency.

### 4. Lambda Jobs

Run these Lambdas outside the VPC unless they need private resources. Outside-VPC Lambdas can call public internet endpoints without NAT.

One JWKS Lambda and one FX Lambda can serve both dev and prod.

JWKS Lambda:

- Trigger: EventBridge schedule every 1 to 6 hours.
- Fetch Google JWKS from `https://www.googleapis.com/oauth2/v3/certs`.
- Preserve the previous DynamoDB item if the fetch fails.
- Parse `Cache-Control: max-age=<seconds>` when present.
- Set `expiresAt` from the cache max-age. If missing, use a conservative default such as 6 hours.
- Write the full JWKS JSON to `AuthJwks`.
- Do not filter keys down to one `kid`; store the whole JWKS because providers rotate keys.

FX Lambda:

- Keep the existing BoC/CBSA flow.
- Write the latest CAD/USD quote to `ExchangeRates`.
- Preserve the previous good quote on fetch failure.

### 5. IAM

App Runner instance role:

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

JWKS Lambda role:

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

FX Lambda role:

- Existing permissions should allow writes to `ExchangeRates`.

### 6. App Runner

Keep the current two-service setup for this phase.

Frontend App Runner services:

- Keep existing public ingress.
- No VPC connector required.
- Keep `VITE_API_BASE_URL` pointed at the backend custom domain, for example `https://api-dev.tradelog.ca/api/v1`.

Backend App Runner services:

- `transaction-api-dev`
- `transaction-api-prod`
- Source: backend ECR image.
- Incoming traffic: public.
- Outgoing traffic: custom VPC connector.
- VPC connector: private subnets and `backend-apprunner-egress-sg`.
- No NAT Gateway.

Backend runtime env:

```properties
APP_AWS_REGION=<aws-region>

DATABASE_URL=<neon-jdbc-url>
DB_MAX_POOL_SIZE=3
DB_MIN_IDLE=0

APP_FX_SOURCE=dynamo
APP_FX_DYNAMO_TABLE=ExchangeRates

APP_SECURITY_JWT_ENABLED=true
APP_SECURITY_ALLOW_HEADER_AUTH=false
APP_SECURITY_JWT_ISSUER_URI=https://accounts.google.com
APP_SECURITY_JWT_AUDIENCE=<google-client-id>
APP_SECURITY_JWT_JWK_SOURCE=dynamo
APP_SECURITY_JWT_JWS_ALGORITHMS=RS256
APP_SECURITY_JWT_JWK_REFRESH_INTERVAL=PT15M
APP_SECURITY_JWT_DYNAMO_TABLE=AuthJwks
APP_SECURITY_JWT_DYNAMO_KEY_ATTRIBUTE=provider
APP_SECURITY_JWT_DYNAMO_KEY=google
APP_SECURITY_JWT_DYNAMO_JWK_SET_ATTRIBUTE=jwks
APP_SECURITY_JWT_DYNAMO_EXPIRES_AT_ATTRIBUTE=expiresAt
APP_SECURITY_JWT_DYNAMO_MAX_STALE=PT72H

APP_SECURITY_ALLOWED_EMAILS=<comma-separated-allowed-emails>
APP_SECURITY_ADMIN_EMAILS=<comma-separated-admin-emails>
```

Keep frontend build args as they are today, except make sure each frontend points to the correct backend custom domain and uses the same Google OAuth client ID/audience as the backend.

## Dev to Prod Data Promotion

Using the current dev database as the initial prod database is acceptable for a small project if you treat it as a one-time seed, not an ongoing dev-to-prod replication stream.

Before importing dev into prod:

- Keep a prod backup/restore point.
- Decide whether prod should leave `APP_SECURITY_ALLOWED_EMAILS` empty for public self-service signup or restrict it for a private rollout.
- Consider pruning blocked users, stale share links, and obvious test data.
- Use the same Google OAuth client ID if you want existing `authId` values to remain stable.
- After import, keep dev and prod databases separate.

Do not allow dev writes to continuously replicate into prod. That would make the open dev login posture much harder to reason about.

## Verification

Before flipping DNS:

- Confirm the JWKS Lambda writes `AuthJwks/provider=google`.
- Confirm the FX Lambda writes `ExchangeRates/pair=CADUSD`.
- From a temporary EC2 instance in the same VPC, run DNS lookup against the Neon hostname and confirm it resolves to private endpoint IPs.
- Confirm App Runner health returns `UP`.
- Sign in and confirm the backend validates JWTs without outbound internet.
- After private DB path works, block public Neon connections.

Failure modes to watch:

- If JWKS is missing or stale beyond `APP_SECURITY_JWT_DYNAMO_MAX_STALE`, API auth fails.
- If provider rotates keys and Lambda is broken, new logins can fail after old keys disappear.
- If DynamoDB gateway endpoint is missing from the route tables, App Runner cannot read FX/JWKS with no NAT.
- If the Neon endpoint security group does not allow `5432` from the App Runner connector security group, DB startup fails.
