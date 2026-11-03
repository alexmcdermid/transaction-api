# Architecture

Diagrams for the day-trade journal as it exists on `main`: the `tradingView` frontend (React Router SSR) and the `transaction-api` backend (Spring Boot).

## Runtime and deployment

Both App Runner services have public ingress; only the prod backend uses VPC egress (no NAT) to reach Neon over PrivateLink and DynamoDB over a gateway endpoint, while dev uses public egress. The scheduled Lambdas that fill DynamoDB are not in these repos; they are described in [Private App Runner + Neon Deployment](private-app-runner-neon.md).

```mermaid
flowchart LR
    browser["Browser"]
    google_signin["Google Identity Services"]
    neon[("Neon Postgres, separate dev and prod DBs")]
    fx_api["BoC / CBSA FX API"]
    google_jwks["Google JWKS endpoint"]

    subgraph aws["AWS"]
        ecr["ECR repos, dev and prod"]
        frontend["Frontend App Runner, React Router SSR, port 3000"]
        backend["Backend App Runner, Spring Boot, port 8080"]
        dynamo[("DynamoDB: ExchangeRates, AuthJwks")]

        subgraph prod_vpc["Prod VPC, no NAT"]
            vpc_connector["VPC connector"]
            neon_privatelink["Neon PrivateLink endpoints"]
            dynamo_gateway["DynamoDB gateway endpoint"]
        end

        subgraph scheduled_jobs["Scheduled jobs, described in docs"]
            eventbridge["EventBridge schedule"]
            fx_lambda["FX Lambda"]
            jwks_lambda["JWKS Lambda"]
        end
    end

    browser -->|"page loads"| frontend
    browser -->|"Google sign-in"| google_signin
    browser -->|"/api/v1 calls, session cookie"| backend
    frontend -->|"GET /api/v1/shares/{code} during SSR"| backend
    ecr -->|"image"| frontend
    ecr -->|"image"| backend

    backend -->|"prod egress"| vpc_connector
    vpc_connector --> neon_privatelink
    vpc_connector --> dynamo_gateway
    neon_privatelink -->|"Postgres"| neon
    dynamo_gateway -->|"GetItem"| dynamo
    backend -.->|"dev: public egress"| neon
    backend -.->|"dev: public egress, GetItem"| dynamo

    eventbridge --> fx_lambda
    eventbridge --> jwks_lambda
    fx_lambda -->|"fetch CAD/USD"| fx_api
    jwks_lambda -->|"fetch keys"| google_jwks
    fx_lambda -->|"write pair=CADUSD"| dynamo
    jwks_lambda -->|"write provider=google"| dynamo
```

## Sign-in and request auth

The browser sends the Google credential to the backend once. After that, the backend's session cookie (`JSESSIONID`, held in the running instance) authenticates requests, and unsafe methods also send the `X-XSRF-TOKEN` header returned by `GET /api/v1/auth/csrf`.

```mermaid
sequenceDiagram
    autonumber
    participant browser as Browser
    participant google as Google Identity Services
    participant api as Backend API
    participant dynamo as DynamoDB AuthJwks
    participant db as Postgres

    browser->>api: GET /api/v1/users/me with credentials include
    api-->>browser: 204 when there is no session, else 200 profile
    browser->>google: Sign-in button or One Tap
    google-->>browser: ID token credential
    browser->>api: GET /api/v1/auth/csrf
    api-->>browser: headerName X-XSRF-TOKEN and token, Set-Cookie XSRF-TOKEN
    browser->>api: POST /api/v1/auth/login with credential and X-XSRF-TOKEN
    opt No cached keys, cache older than 15 minutes, or first decode fails
        api->>dynamo: GetItem provider=google
        dynamo-->>api: jwks and expiresAt, rejected if 72h past expiresAt
    end
    api->>api: Verify RS256 signature, expiry, issuer and audience, else 401 Invalid credential
    api->>api: Email allowlist check when one is configured (dev sets one), else 403
    api->>db: Get or create user by auth_id (Google sub)
    api->>api: Create or rotate session, store principal
    api-->>browser: 200 profile, Set-Cookie JSESSIONID (HttpOnly, Secure, SameSite=Lax)
    opt Terms or privacy policy not yet accepted
        browser->>api: POST /api/v1/users/me/legal-agreement with X-XSRF-TOKEN
        api-->>browser: 200 profile
    end
    browser->>api: Later calls, e.g. GET /api/v1/trades, cookies sent
    Note over browser,api: POST, PUT and DELETE also send X-XSRF-TOKEN
    api->>api: Filters: 7-day absolute session limit, rate limit, legal agreement
    api->>db: Queries scoped to the caller's user_id
    api-->>browser: JSON
    browser->>api: POST /api/v1/auth/logout with X-XSRF-TOKEN
    api-->>browser: 204, session invalidated, JSESSIONID and XSRF-TOKEN cleared
```

## CI/CD

Both repos run the same lifecycle against one shared dev environment. CI runs only on PRs to `main` (pushes to `main` run nothing), and prod deploys are manual.

```mermaid
flowchart TD
    pr_event["PR to main: opened, synchronize, reopened, ready_for_review"]

    subgraph ci_test["CI: test job"]
        frontend_checks["Frontend: typecheck, Vitest, npm audit"]
        backend_checks["Backend: mvn test"]
        image_scan["Docker build and Trivy scan, fails on CRITICAL"]
    end

    deploy_gate{"Non-draft PR from this repo?"}
    tests_only["Done, no deploy"]

    subgraph deploy_pr_dev["CI: deploy-pr-dev job"]
        assume_dev_role["Assume AWS role via OIDC, dev environment"]
        resume_dev["Resume dev backend and dev frontend"]
        push_pr_image["Build and push image tagged pr-N-SHA to dev ECR"]
        update_dev["Update this repo's dev App Runner service, wait for RUNNING"]
    end

    pr_closed["PR to main closed or merged, pull_request_target"]
    count_prs["Check out base branch, assume OIDC role, count open non-draft same-repo PRs in both repos"]
    idle_check{"Count is 0?"}
    pause_dev["Pause dev frontend and dev backend"]
    keep_dev["Leave dev running"]

    manual_prod["Manual workflow_dispatch: Deploy (Prod)"]
    deploy_prod["Assume OIDC role, prod environment. Build and push image tagged with commit SHA, update prod App Runner"]

    review_comment["PR comment: /ai-review"]
    review_job["AI PR Review job posts review comments"]

    regression["Frontend only: Cypress regression, monthly cron or manual"]

    pr_event -->|"frontend repo"| frontend_checks
    pr_event -->|"backend repo"| backend_checks
    frontend_checks --> image_scan
    backend_checks --> image_scan
    image_scan -->|"pass"| deploy_gate
    deploy_gate -->|"no"| tests_only
    deploy_gate -->|"yes"| assume_dev_role
    assume_dev_role --> resume_dev
    resume_dev --> push_pr_image
    push_pr_image --> update_dev

    pr_closed --> count_prs
    count_prs --> idle_check
    idle_check -->|"yes"| pause_dev
    idle_check -->|"no"| keep_dev

    manual_prod --> deploy_prod
    review_comment --> review_job
```

## Data model

Tables from the Flyway migrations `V1` to `V17` in `src/main/resources/db/migration`; the only foreign key is `trades.account_id`. Dashed links have no constraint: `user_id` columns hold the user's `auth_id` (Google `sub`), and `trade_history` keeps its rows after a trade is deleted.

```mermaid
erDiagram
    users {
        uuid id PK
        varchar auth_id UK "Google sub"
        varchar email
        boolean premium
        varchar theme_mode "plus other display preferences"
        varchar display_currency
        timestamptz terms_accepted_at
        timestamptz privacy_policy_accepted_at
    }
    accounts {
        uuid id PK
        varchar user_id "users.auth_id"
        varchar name
        numeric default_stock_fees
        numeric default_option_fees
        boolean tax_free
    }
    trades {
        uuid id PK
        varchar user_id "users.auth_id"
        uuid account_id FK "nullable"
        varchar symbol
        varchar asset_type "STOCK or OPTION"
        varchar direction "LONG or SHORT"
        varchar currency "USD or CAD"
        numeric quantity
        numeric entry_price
        numeric exit_price
        numeric fees
        numeric margin_rate
        numeric realized_pnl
        date opened_at
        date closed_at
        varchar option_type "CALL or PUT, options only"
        numeric strike_price "options only"
        date expiry_date "options only"
    }
    trade_history {
        uuid id PK
        uuid trade_id "trades.id"
        varchar action "CREATE, EDIT or DELETE"
        varchar user_id
        timestamptz action_at "plus a full trade snapshot"
    }
    share_links {
        uuid id PK
        varchar code UK
        varchar user_id "users.auth_id"
        varchar share_type "SUMMARY or TRADES"
        jsonb data
        timestamptz expires_at
        integer access_count
    }
    exchange_rates {
        uuid id PK
        varchar base_currency
        varchar quote_currency
        numeric rate
        date effective_date "unique per currency pair"
    }

    users ||..o{ accounts : "user_id"
    users ||..o{ trades : "user_id"
    users ||..o{ share_links : "user_id"
    accounts |o--o{ trades : "account_id, on delete set null"
    trades |o..o{ trade_history : "trade_id"
```

## AI PR review

A `/ai-review` comment from the repo owner, a member or a collaborator runs the workflow from `main` with a read-only checkout and `pull-requests: write`. Each selected provider posts its own review as a comment; it never approves or blocks.

```mermaid
sequenceDiagram
    autonumber
    actor reviewer as Owner, member or collaborator
    participant github as GitHub
    participant job as ai-review job
    participant anthropic as Anthropic API
    participant openai as OpenAI API

    reviewer->>github: Comment /ai-review [light|standard|deep|security] [anthropic|openai|both]
    github->>job: issue_comment created, workflow runs from main
    job->>github: Add eyes reaction to the comment
    job->>github: Get PR, changed files, and full files at head for standard, deep and security
    par Anthropic selected
        job->>anthropic: Messages API with JSON schema output
        anthropic-->>job: Summary and findings
    and OpenAI selected
        job->>openai: Responses API with JSON schema output, security tier uses Daybreak Blue
        openai-->>job: Summary and findings
    end
    job->>github: Post one COMMENT review per provider, with inline comments
    Note over job,github: If GitHub rejects inline positions, findings move into the review body. A failed provider posts a PR comment instead.
```
