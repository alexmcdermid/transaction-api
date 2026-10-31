# Tax Year Pass: Billing, Entitlements, and Reports

A **tax year pass** is a one-time purchase that unlocks tradelog reports and exports for one
calendar tax year, like buying tax software for a filing season. There is no subscription and no
recurring charge. Buying 2025 never unlocks 2024 or 2026.

This replaces the earlier subscription scaffold (`feat/stripe-subscriptions`, transaction-api PR #22
and tradingView PR #32).

## Why Payment Links instead of the Checkout Sessions API

The prod backend runs on App Runner with **VPC egress and no NAT** (see
[private-app-runner-neon.md](private-app-runner-neon.md)). It cannot reach `api.stripe.com`, so any
design where the backend creates Checkout Sessions, Customers, or Prices fails in prod.

Inbound traffic still works, so the backend never calls Stripe:

1. The backend creates a `PENDING` pass row and returns the Stripe **Payment Link** URL with
   `client_reference_id=<pass id>`.
2. The browser goes to Stripe, the customer pays, and Stripe redirects back to `/reports`.
3. Stripe sends a signed webhook to the backend. Signature verification is a local HMAC check. The
   webhook checks that the session came from the tax year pass Payment Link, resolves the pass by
   `client_reference_id`, and marks it `PAID`.

Dev and local use the same flow with a test-mode Payment Link, so there is one code path.

## Data model

`tax_year_passes` (migration `V18__tax_year_passes.sql`). Each row is one purchase attempt.

| Column | Notes |
| --- | --- |
| `id` | UUID. Also the Payment Link `client_reference_id`. Unguessable. |
| `user_id` | FK to `users.id` (internal UUID, not the auth id). |
| `tax_year` | Calendar year of trade `closed_at` dates. `CHECK (tax_year >= 2000)`. |
| `status` | `PENDING`, `PROCESSING`, `PAID`, `FAILED`, `REFUNDED`. A `CHECK` allows only these, so a typo in hand-written SQL fails at once instead of making that user's overview and pass list fail to load. |
| `stripe_checkout_session_id` | Unique. Set from the completed checkout session. |
| `stripe_payment_intent_id` | Used to match `charge.refunded`. |
| `stripe_customer_id` | Support lookups only. |
| `amount_total`, `currency` | What Stripe actually charged (minor units, upper-case ISO). Promo codes make this differ from the list price. |
| `paid_at`, `refunded_at` | Audit timestamps. |

Status transitions (`TaxYearPassStatus.canTransitionTo`) are monotonic, so replayed or out-of-order
webhooks are harmless:

```text
PENDING    -> PROCESSING | PAID | FAILED
PROCESSING -> PAID | FAILED
FAILED     -> PROCESSING | PAID      (customer retries the same pass)
PAID       -> REFUNDED
REFUNDED   -> (terminal; buying again creates a new PENDING pass)
```

**Entitlement:** a user may read a tax year's reports only while a `PAID` pass exists for that user
and year. `TaxYearPassService.requirePaidPass(user, taxYear)` is the only gate, and every report and
export endpoint calls it before querying trades. The error is `403` with the stable message
`Tax year pass required for <year>`.

`User.premium` is unrelated legacy state and is not used for gating.

## Configuration

| Property | Env var | Purpose |
| --- | --- | --- |
| `app.billing.stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | Webhook signing secret (`whsec_...`). |
| `app.billing.tax-year-pass.payment-link-url` | `APP_TAX_YEAR_PASS_PAYMENT_LINK_URL` | Payment Link URL (`https://buy.stripe.com/...`). |
| `app.billing.tax-year-pass.payment-link-id` | `APP_TAX_YEAR_PASS_PAYMENT_LINK_ID` | That Payment Link's id (`plink_...`). The webhook ignores checkout sessions from any other link. |
| `app.billing.tax-year-pass.price-cents` | `APP_TAX_YEAR_PASS_PRICE_CENTS` | Optional display price, e.g. `3900`. |
| `app.billing.tax-year-pass.price-currency` | `APP_TAX_YEAR_PASS_PRICE_CURRENCY` | Optional display currency, e.g. `CAD`. |

Billing is enabled only when the webhook secret, Payment Link URL and Payment Link id are all set.
Leave all three blank to disable it: reports stay locked, `POST .../checkout` returns
`503 Tax year pass checkout is not configured`, and the webhook returns
`503 Stripe webhook is not configured`. `BillingSettings` refuses to start on partial or malformed
configuration: only some of the three set, a non-`whsec_` secret, a non-https link, an id that does
not start with `plink_`, or half a display price. The display price is only for the UI. Stripe is
the source of truth for charges, so keep it in sync with the Payment Link price by hand.

No Stripe secret API key is needed, because the backend makes no Stripe API calls.

## Stripe setup runbook

Do this once in test mode for local/dev, then again in live mode for prod.

1. **Product and price:** Products -> Add product `tradelog Tax Year Pass`. Add a one-time price,
   e.g. 39.00 CAD.
2. **Payment Link:** Payment Links -> New, using that price.
   - Quantity: fixed at 1. Do not let customers adjust quantity.
   - After payment: *Don't show confirmation page* -> redirect to
     `https://<frontend-origin>/reports?checkout=success&session_id={CHECKOUT_SESSION_ID}`
     (local: `http://localhost:5173/reports?checkout=success&session_id={CHECKOUT_SESSION_ID}`).
   - Optional: allow promotion codes. A 100% code gives a zero-amount `PAID` pass, which is handy
     for comps and testing.
   - Optional: enable Stripe Tax for GST/HST once registered. No code change is needed.
   - Copy the link URL into `APP_TAX_YEAR_PASS_PAYMENT_LINK_URL`, and the id shown on the Payment
     Link's page (`plink_...`) into `APP_TAX_YEAR_PASS_PAYMENT_LINK_ID`. A new Payment Link, for
     example for a new price, needs both updated together. Sessions completed on the old link after
     the switch are ignored, so unlock or refund those by hand.
3. **Webhook endpoint:** Developers -> Webhooks -> Add endpoint
   `https://<api-origin>/api/v1/billing/stripe/webhook`. Select these events:
   - `checkout.session.completed`
   - `checkout.session.async_payment_succeeded`
   - `checkout.session.async_payment_failed`
   - `charge.refunded`

   Copy the signing secret into `STRIPE_WEBHOOK_SECRET`.
4. **App Runner env:** add the five env vars through GitHub environment secrets.
   `.github/workflows/ci.yml` (dev) and `deploy-prod.yml` (prod) pass them through as optional
   runtime variables: `DEV_STRIPE_WEBHOOK_SECRET`, `DEV_TAX_YEAR_PASS_PAYMENT_LINK_URL`,
   `DEV_TAX_YEAR_PASS_PAYMENT_LINK_ID`, `DEV_TAX_YEAR_PASS_PRICE_CENTS`,
   `DEV_TAX_YEAR_PASS_PRICE_CURRENCY`, with the same names prefixed `PROD_` for prod. An environment
   that has the secret and URL but no id will not start, so add the id first.

### Local testing

```bash
stripe login
stripe listen --forward-to localhost:8080/api/v1/billing/stripe/webhook
# copy the printed whsec_... into STRIPE_WEBHOOK_SECRET, set the test-mode Payment Link's URL and
# plink_ id, then start the backend
```

Use the app's **Unlock** button so the link carries a real pass id. Then pay with test card
`4242 4242 4242 4242`, any future expiry, and any CVC. `stripe trigger checkout.session.completed`
sends a session that did not come from the Payment Link, which the webhook acknowledges and ignores
by design.

To unlock a year locally without Stripe:

```sql
INSERT INTO tax_year_passes (id, user_id, tax_year, status, paid_at, created_at, updated_at)
SELECT gen_random_uuid(), id, 2025, 'PAID', now(), now(), now() FROM users WHERE auth_id = '<auth id>';
```

## Webhook handling

`POST /api/v1/billing/stripe/webhook` is public (no session, no CSRF, no legal-agreement gate). The
Stripe signature is the authentication. It verifies `Stripe-Signature` against the raw body with
`Webhook.constructEvent` and the default 5-minute tolerance.

**Payment Link binding:** a checkout session event changes a pass only when the session has
`mode=payment` and its `payment_link` is the configured `plink_` id. The pass id in
`client_reference_id` is visible in the buyer's checkout URL, so without this check it could be
appended to any other Payment Link in the account, such as a cheaper price or a free-trial
subscription that completes with `no_payment_required`. Any other session is logged at `WARN`
(event id, type, `payment_link`, `mode`) and acknowledged with `200` before a pass is looked up.
`charge.refunded` needs no check: it only matches payment intents recorded from bound sessions.

| Event | Effect on the pass resolved by `client_reference_id` |
| --- | --- |
| `checkout.session.*` from another Payment Link, with no `payment_link`, or not in `mode=payment` | No change; no pass is looked up. `WARN`, `200` |
| `checkout.session.completed`, `payment_status=paid` or `no_payment_required` | -> `PAID`; record session, payment intent, customer, amount, currency, `paid_at` |
| `checkout.session.completed`, `payment_status=unpaid` (delayed methods such as ACSS debit) | -> `PROCESSING`; record session, payment intent, customer, amount, currency |
| `checkout.session.async_payment_succeeded` | -> `PAID` |
| `checkout.session.async_payment_failed` | -> `FAILED` |
| `charge.refunded` with `refunded=true` (full refund) | Pass matched by `payment_intent` -> `REFUNDED`, `refunded_at` |
| `charge.refunded` partial refund | No change. Access is kept. |
| anything else | Ignored, `200` |

Rules:

- Responses: `200` on success, on ignored events, on sessions that fail the Payment Link binding,
  and on sessions whose `client_reference_id` is missing, malformed, or unknown. Those are logged
  and acknowledged so Stripe stops retrying; they need manual support. `400` means a bad or missing
  signature. `503 Stripe webhook is not configured` means billing is off.
  `500 Stripe event could not be processed` means an unknown `payment_status` on
  `checkout.session.completed` or a data object that cannot be read; it is logged at `ERROR`, and
  Stripe retries the event. Any other unexpected error is also a `5xx`.
- Rows are locked with a native `SELECT ... FOR UPDATE` (`findByIdForUpdate`,
  `findByStripePaymentIntentIdForUpdate`) during a transition. Native, because Hibernate renders
  `@Lock(PESSIMISTIC_WRITE)` on PostgreSQL as `FOR NO KEY UPDATE`, which the H2 test database cannot
  parse; the two differ only for foreign keys into `tax_year_passes`, and there are none.
- A transition that `canTransitionTo` rejects is a no-op, so replays and out-of-order deliveries are
  safe. Two stale deliveries that it allows are no-ops too: a late `unpaid` completion for a session
  that already failed (`FAILED -> PROCESSING` is for a retry with a new session), and
  `async_payment_failed` from any session other than the one a `PROCESSING` pass is waiting on.
- **Duplicate payment:** if a pass is already `PAID` or `REFUNDED` and a different checkout session
  completes for it, nothing is overwritten. Log a `WARN` with the pass id, session id, and payment
  intent so an operator can refund it in Stripe.
- **API version skew:** stripe-java pins an API version. When the endpoint's version differs,
  `EventDataObjectDeserializer.getObject()` is empty, so fall back to `deserializeUnsafe()`.
- Never log emails, card data, or full payloads. Log only ids (event, pass, checkout session,
  payment intent, payment link), the event type, the session mode, and the outcome.

## Billing API

All routes require a session except the webhook.

### `GET /api/v1/billing/tax-year-passes`

The caller's passes, newest tax year first and then newest pass first, for purchase history and
post-checkout polling.

```json
[
  {
    "id": "8a6c...",
    "taxYear": 2025,
    "status": "PAID",
    "amountTotal": 3900,
    "currency": "CAD",
    "paidAt": "2026-02-03T18:22:11Z",
    "refundedAt": null,
    "createdAt": "2026-02-03T18:20:40Z"
  }
]
```

### `POST /api/v1/billing/tax-year-passes/{taxYear}/checkout`

Creates a `PENDING` pass, or reuses the newest `PENDING`/`FAILED` pass for that user and year, and
returns the Payment Link URL for the browser to open.

```json
{ "checkoutUrl": "https://buy.stripe.com/test_abc?client_reference_id=8a6c...&prefilled_email=a%40b.com" }
```

- `client_reference_id` is the pass id. `prefilled_email` is the account email, URL-encoded, and
  omitted when unknown. `+` must be encoded as `%2B`.
- `400`: tax year outside `2000..currentYear`.
- `409`: already `PAID` (`Tax year <year> is already unlocked`) or a payment is `PROCESSING`
  (`A payment for tax year <year> is still processing`).
- `503`: checkout not configured (`Tax year pass checkout is not configured`).

## Reports API

The overview is free. Everything else requires a `PAID` pass for `{taxYear}`. The tax year is the
calendar year of each trade's `closed_at`. All amounts stay in each trade's **recorded currency**.
Nothing is FX-converted, and every aggregate row carries a `currency`.

### `GET /api/v1/reports/tax-years` (free)

```json
{
  "currentTaxYear": 2026,
  "checkoutEnabled": true,
  "price": { "amountCents": 3900, "currency": "CAD" },
  "years": [
    { "taxYear": 2026, "closedTradeCount": 120, "passStatus": null, "unlocked": false, "purchasable": true },
    { "taxYear": 2025, "closedTradeCount": 412, "passStatus": "PAID", "unlocked": true, "purchasable": false }
  ]
}
```

- `years` holds every year with at least one closed trade, plus `currentTaxYear`, newest first.
- `passStatus` is the most significant status for that year (PAID > PROCESSING > PENDING > FAILED >
  REFUNDED), or `null`.
- `unlocked` is `passStatus == PAID`.
- `purchasable` is `checkoutEnabled && !unlocked && passStatus != PROCESSING && 2000 <= taxYear <= currentTaxYear`.
- `price` is `null` when no display price is configured.

### Report filters (shared request shape)

```json
{
  "accountIds": ["5b6ab7cd-..."],
  "includeUnassigned": true,
  "symbols": ["AAPL"],
  "assetTypes": ["STOCK", "OPTION"],
  "currencies": ["USD", "CAD"],
  "directions": ["LONG", "SHORT"]
}
```

Every field is optional, and an empty body means everything for the year.

- `accountIds`: an empty list means every account. Each requested id must belong to the caller,
  otherwise `403 Account access denied`. Unowned ids are not silently dropped. Max 100.
- `includeUnassigned`: trades with no account, added to the selected accounts. **Defaults to
  `true`** when omitted or null. Explicit `false` excludes them.
- `symbols`: trimmed and upper-cased, max 100, each at most 12 characters.
- `assetTypes`, `currencies`, `directions`: an empty list means no filter. Max 10 each.
- Rows sort by `closed_at`, then trade id, so output is deterministic.
- Checks run in this order: request-body validation (`400`), the tax year range
  (`400 Tax year must be between 2000 and <current year>`), the `PAID` pass (`403`), then account
  ownership (`403`). An invalid body is a `400` even without a pass, and an unowned account id is
  only reported once the year is unlocked.

### `POST /api/v1/reports/tax-years/{taxYear}/summary` (pass required)

Body: report filters (optional). Response:

```json
{
  "taxYear": 2025,
  "tradeCount": 412,
  "firstClosedOn": "2025-01-03",
  "lastClosedOn": "2025-12-30",
  "repurchaseFlagCount": 7,
  "totals":       [ SummaryRow ],
  "months":       [ SummaryRow ],
  "accounts":     [ SummaryRow ],
  "symbols":      [ SummaryRow ],
  "assetTypes":   [ SummaryRow ],
  "taxTreatment": [ SummaryRow ]
}
```

A year with no matching trades returns `tradeCount: 0`, empty arrays, and `null` for
`firstClosedOn` and `lastClosedOn`.

`SummaryRow`, one per group and currency:

```json
{
  "key": "2025-01",
  "label": "2025-01",
  "currency": "USD",
  "taxFree": null,
  "tradeCount": 31,
  "winningTrades": 19,
  "losingTrades": 11,
  "gains": 4210.55,
  "losses": -1980.10,
  "fees": 61.25,
  "marginInterest": 3.10,
  "netRealizedPnl": 2230.45
}
```

| Section | `key` / `label` | Order |
| --- | --- | --- |
| `totals` | `ALL` / `All trades` | by currency |
| `months` | `YYYY-MM` / `YYYY-MM` | chronological, then currency |
| `accounts` | account id or `UNASSIGNED` / account name or `Unassigned`; `taxFree` set (unassigned = `false`) | label (case-insensitive), then currency, then key |
| `symbols` | symbol / symbol | `netRealizedPnl` desc, then symbol, then currency |
| `assetTypes` | `STOCK` / `Stocks`, `OPTION_CALL` / `Calls`, `OPTION_PUT` / `Puts` | that order, then currency |
| `taxTreatment` | `TAXABLE` / `Taxable accounts`, `TAX_FREE` / `Tax-free accounts` (unassigned trades count as taxable) | that order, then currency |

Currency ties sort by alphabetical ISO code, so `CAD` comes before `USD`.

Metric definitions, applied identically to every group:

- `tradeCount`: closed trades in the group. Breakeven trades (P/L exactly 0) count here but are
  neither winning nor losing.
- `winningTrades` / `losingTrades`: recorded realized P/L `> 0` / `< 0`.
- `gains` / `losses`: sum of recorded realized P/L of winning / losing trades (`losses <= 0`).
- `fees`: sum of recorded fees.
- `marginInterest`: sum of estimated margin interest, using the same formula that computes realized
  P/L (`TradeCalculations.marginInterest`).
- `netRealizedPnl`: sum of recorded realized P/L (`gains + losses`).
- All money is scaled to 2 decimals, `HALF_UP`.
- `taxFree`: only set on `accounts` rows (and `taxTreatment`, where it mirrors the key). It is
  `null` elsewhere.
- `repurchaseFlagCount`: distinct loss trades in the repurchase review with at least one match
  where `possible_same_position` is false.

### `POST /api/v1/reports/tax-years/{taxYear}/exports` (pass required)

```json
{ "reportType": "TRADE_LEDGER", "filters": { }, "includeNotes": false }
```

- `reportType` is required: `TRADE_LEDGER`, `DISPOSITIONS`, `ANNUAL_SUMMARY`, or
  `REPURCHASE_REVIEW`.
- `filters` is optional (shape above).
- `includeNotes` only affects `TRADE_LEDGER`. It is a `Boolean`, so omitted or `null` means `false`.
  It is not a primitive because Jackson 3 fails on `null` primitives (`FAIL_ON_NULL_FOR_PRIMITIVES`
  is on by default).
- Response `200`, `Content-Type: text/csv; charset=UTF-8`,
  `Content-Disposition: attachment; filename="tradelog-<year>-<slug>.csv"`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`. The body is CSV whatever the
  `Accept` header asks for.
- The frontend calls the API cross-origin, so CORS exposes `Content-Disposition`
  (`Access-Control-Expose-Headers`, set in both `SecurityConfig` and `CorsConfig`) for it to read the
  filename.
- Slugs: `trade-ledger`, `dispositions`, `annual-summary`, `repurchase-review`.
- A valid empty result returns the header row only.

CSV rules, shared by every export:

- Use Apache Commons CSV (`CSVFormat.DEFAULT`, CRLF records). Never hand-roll quoting.
- **Formula injection:** text cells (symbols, account names, notes, descriptions, labels) starting
  with `=`, `+`, `-`, `@`, tab, CR, or LF get a leading `'`. Only the first character is checked.
  Numeric cells are written as plain decimals and are never prefixed.
- Decimals use `BigDecimal.toPlainString()`, so there is no exponent notation and no locale
  grouping. Money always has 2 decimals (`HALF_UP`). Quantities, prices, strikes, and margin rates
  drop trailing zeros, e.g. `100`, `1.1`, `150`. Dates use ISO `yyyy-MM-dd` and instants ISO-8601
  UTC. Booleans are `true`/`false`, blank when unknown.
- UTF-8 without a byte order mark.
- No auth ids, emails, internal user ids, or Stripe ids. Never log CSV contents or notes.

#### `TRADE_LEDGER`: every filtered closed trade, as recorded

```text
trade_id,account_id,account_name,account_tax_free,symbol,asset_type,option_type,strike_price,
expiry_date,currency,direction,quantity,entry_price,exit_price,fees,margin_rate,margin_interest,
opened_at,closed_at,gross_pnl,recorded_realized_pnl,notes,created_at,updated_at
```

- `gross_pnl` = `(exit - entry) * quantity * multiplier`, negated for `SHORT`, 2 decimals.
  `multiplier` is 100 for options and 1 for stocks.
- `margin_interest` comes from `TradeCalculations.marginInterest`.
  `recorded_realized_pnl = gross_pnl - fees - margin_interest` as stored on the trade.
- `notes` is blank unless `includeNotes=true`. The column always exists.
- Unassigned trades have a blank `account_id`, `account_name` `Unassigned`, and `account_tax_free`
  `false`.

#### `DISPOSITIONS`: proceeds and cost per closed trade, taxable accounts only

Schedule 3 / Form 8949 style input for an accountant. Trades in tax-free accounts are excluded.
Unassigned trades are included.

```text
trade_id,description,symbol,asset_type,position,quantity,opened_on,closed_on,currency,
proceeds,cost,outlays_and_expenses,gain_loss,account_name
```

- `description`: stocks `"<qty> <SYMBOL>"`, e.g. `100 AAPL`. Options
  `"<qty> <SYMBOL> <expiry> <strike> <CALL|PUT>"`, e.g. `2 AAPL 2025-03-21 150 CALL`. Quantities and
  strikes have trailing zeros stripped.
- `LONG`: `proceeds = exit * qty * multiplier`, `cost = entry * qty * multiplier`.
- `SHORT`: `proceeds = entry * qty * multiplier`, `cost = exit * qty * multiplier`.
- `proceeds` and `cost` are rounded to 2 decimals, `HALF_UP`.
- `outlays_and_expenses` = recorded fees. A trade records a single fee amount, so buying and selling
  commissions both land here instead of being split between `cost` and outlays; the gain is the same
  either way.
- `gain_loss` = `proceeds - cost - outlays_and_expenses`, so every row foots exactly.
- Estimated margin interest is left out. It is a cost of borrowing, not of the sale, and an estimate
  besides; `TRADE_LEDGER` and `ANNUAL_SUMMARY` report it. So `gain_loss` = recorded realized P/L +
  estimated margin interest, give or take 0.01 from rounding `proceeds` and `cost` separately.

#### `ANNUAL_SUMMARY`: the summary sections as one long-format CSV

```text
section,key,label,currency,tax_free,trade_count,winning_trades,losing_trades,gains,losses,fees,
margin_interest,net_realized_pnl
```

`section` is `TOTAL`, `MONTH`, `ACCOUNT`, `SYMBOL`, `ASSET_TYPE`, or `TAX_TREATMENT`, emitted in
that order with the same rows and ordering as the JSON summary.

#### `REPURCHASE_REVIEW`: losses with a same-symbol position opened within 30 days

The review list is meant for someone checking superficial loss (CRA) or wash sale (IRS) exposure.
**No adjustments are applied.**

```text
loss_trade_id,symbol,loss_closed_on,loss_currency,loss_amount,loss_account_name,match_trade_id,
match_asset_type,match_direction,match_opened_on,match_account_name,match_account_tax_free,
days_from_loss,possible_same_position
```

- **Loss trades** are in the tax year, pass the filters, sit in a taxable account (unassigned
  counts as taxable), and have recorded realized P/L `< 0`.
- **Matches** come from all of the caller's trades, in any account including tax-free ones, with
  report filters **ignored**. A match has the same symbol, a different trade id, and
  `opened_at` within `[loss.closed_at - 30 days, loss.closed_at + 30 days]` inclusive. The window
  crosses year boundaries.
- `days_from_loss` = `match.opened_at - loss.closed_at` in days, signed.
- `possible_same_position` = the match has the same `opened_at`, account (including both
  unassigned), and direction as the loss trade. This usually means one position closed in parts.
- One row per (loss, match) pair, sorted by loss `closed_at`, loss id, match `opened_at`, match id.
  Losses without matches are omitted.

## Copy guardrails (UI and docs)

- Prefer *tax year pass*, *accountant export*, *recorded realized P/L*, *proceeds and cost*, and
  *review list*.
- Avoid *official tax document*, *tax filing*, *CRA/IRS compliant*, *verified cost basis*, and
  *taxable gain*.
- Keep this disclosure next to exports:
  > Exports summarize trades recorded in tradelog. They are not official tax slips, tax filings,
  > or tax advice. Amounts are in each trade's recorded currency, with no exchange-rate conversion.

## Known limitations

- **Refund before checkout:** a pass learns its payment intent from the checkout event, so a full
  refund delivered before that event matches no pass. It is only logged at `WARN`
  (`... matches no tax year pass ... revoke it by hand`). The pass still unlocks when the checkout
  event arrives, and an operator has to revoke it.
- **Formula-injection guard:** it checks the leading character only. Every text cell is the account
  owner's own data, so a crafted value can only reach that owner's spreadsheet.
- **No UTF-8 BOM:** Excel can mis-decode non-ASCII text, such as accented account names, when it
  opens the file directly. Importing the file as UTF-8 works.

## Not done yet

- **FX conversion** (for example, USD trades in CAD at Bank of Canada daily rates). Prod has no
  egress, and `exchange_rates` only has history from when the app started persisting rates. This
  needs a historical-rate backfill, probably from the existing FX Lambda into DynamoDB.
- **Adjusted cost base across lots, assignments, exercises, corporate actions, transfers.** The
  `Trade` model records closed round trips only.
- **Disputes:** `charge.dispute.*` is not handled. Refund or revoke manually.
- **Admin comps/grants:** use a 100% promotion code for now.
- **Saved or custom report definitions.**
