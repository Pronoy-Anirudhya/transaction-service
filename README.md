# transaction-service

Send Money (wallet to wallet) service of the MFS POC v2. It validates the business rules, prices
the fee / VAT / commission, enforces per-tier limits in PostgreSQL, posts all legs **atomically and
synchronously** to `ledger-service` (TigerBeetle), returns the final status in the same HTTP
response, repairs in-doubt transactions, and publishes `SendMoneyCompleted` / `SendMoneyFailed`
events to RabbitMQ after the outcome is committed. No 2PC, no outbox table.

- **Design:** [`docs/Send Money POC v2 — SRS & Design Spec.md`](docs/Send%20Money%20POC%20v2%20%E2%80%94%20SRS%20%26%20Design%20Spec.md)
  (single source of truth: flow in section 5, data in 6, API in 7, failure handling in 8, events in 9).
- **Decisions where the spec is silent:** [`docs/decisions.md`](docs/decisions.md).
- **Progress:** [`PROGRESS.md`](PROGRESS.md).
- **Contracts:** [`openapi/transaction-api.yaml`](openapi/transaction-api.yaml) (OpenAPI 3.1, authoritative) and
  [`openapi/events/send-money-v1.schema.json`](openapi/events/send-money-v1.schema.json) (JSON Schema 2020-12 of the event payload).

Stack: Java 25, Spring Boot 4.1.1, PostgreSQL 18, RabbitMQ 4.x, TigerBeetle 0.17.x (through `ledger-service`).

## Prerequisites

- JDK 25 (the Gradle wrapper is included)
- Docker with Compose v2.20+ (for the integration tests and the local stack)

## Build and test

```bash
./gradlew clean build
```

Integration tests use Testcontainers (PostgreSQL 18, RabbitMQ 4) and a WireMock ledger, so Docker
must be running; without Docker those tests are skipped automatically and the unit tests still run.

The service needs these settings, which have **no default** in `application.properties` (NFR-09:
no secrets in the repository): `TXN_DB_PASSWORD`, `RABBIT_USER`, `RABBIT_PASSWORD`,
`QUOTE_SIGNING_KEY`, `API_KEY`. Docker Compose supplies local-development values.

## Run the stack

### Standalone (default): WireMock ledger stub

```bash
docker compose up -d --build
docker compose ps        # transaction-service becomes healthy after ~20-40 s
```

Starts `postgres`, `rabbitmq`, `ledger-stub` (WireMock implementing the ledger wire contract) and
`transaction-service` on <http://localhost:8080>. The stub always answers POSTED, except that a
posting whose **principal leg amount is `2499999` or `99999999`** is rejected with
`422 INSUFFICIENT_FUNDS`, so the failure path can be demonstrated. Its balance endpoint returns a
fixed sample.

### Full: real `ledger-service` + TigerBeetle

Needs the sibling checkout `../ledger-service` (it provides its own `Dockerfile`).

```bash
docker compose down                                         # if the default stack is running
docker compose --profile init run --rm tigerbeetle-format   # once: creates the TigerBeetle data file
docker compose --profile full up -d --build
```

`--profile full` starts `tigerbeetle` and `ledger-service` **instead of** the stub. Stop it with
`docker compose --profile full down`. To wipe all data (PostgreSQL, RabbitMQ, TigerBeetle):
`docker compose --profile full down -v` (then format TigerBeetle again).

### Ledger: stub or real

transaction-service always calls `http://ledger:8081` (`LEDGER_BASE_URL`). `ledger` is a Compose
network alias held by whichever ledger runs:

| Command | Ledger container holding the alias `ledger` |
| --- | --- |
| `docker compose up` | `ledger-stub` (WireMock) |
| `docker compose --profile full up` | `ledger-service` (TigerBeetle-backed) |

The stub declares `profiles: [""]`. Compose enables a service with the empty profile only while no
profile is active, so the stub runs by default and drops out as soon as `--profile full` (or
`--profile init`) is given, with no `.env` file needed. Both ledgers hold the same alias, so never
run both: always `docker compose down` before switching modes (a stub container left running from
the default mode would share the `ledger` alias). `docker compose --profile full config --services`
shows what will run.

### Credentials

All credentials in `docker-compose.yml` are local-development defaults (`${VAR:-dev-value}`);
override them in a git-ignored `.env` (template: [`.env.example`](.env.example)).

| What | Default (local dev only) |
| --- | --- |
| API key (`X-API-Key`) | `local-dev-api-key` (`API_KEY`) |
| PostgreSQL `txn_db` | user `txn`, password `txn-local-dev` (`TXN_DB_PASSWORD`) |
| RabbitMQ service user | `txn` / `txn-local-dev` (`RABBIT_USER`, `RABBIT_PASSWORD`) |
| RabbitMQ management UI <http://localhost:15672> | `admin` / `admin-local-dev` |

RabbitMQ users come from `deploy/rabbitmq/definitions.json` (imported at boot together with the
exchange `mfs.transactions`, the DLX `mfs.transactions.dlx`, the demo quorum queue
`audit.send-money` bound to `send-money.#`, and its dead-letter queue `audit.send-money.dlq`).
Because definitions are imported, RabbitMQ creates no `guest` user. To change a password,
regenerate its hash and update the definitions file:

```bash
python3 -c "import os,hashlib,base64,sys; s=os.urandom(4); print(base64.b64encode(s+hashlib.sha256(s+sys.argv[1].encode()).digest()).decode())" 'new-password'
```

## API

- Contract: `GET /openapi.yaml` (no API key) — the file `openapi/transaction-api.yaml`, served unchanged.
- Swagger UI: <http://localhost:8080/swagger-ui.html> (use **Authorize** to enter the API key).
- Every `/api/**` call needs the header `X-API-Key: <API_KEY>`; otherwise `401`.
- Errors are RFC 9457 Problem Details (`application/problem+json`) with a machine-readable `code`.
- Amounts are integer **poisha** (1 BDT = 100 poisha); currency is always `BDT`.

| Method & path | Purpose | Success |
| --- | --- | --- |
| `POST /api/v1/send-money/quote` | Fee, VAT, commission, total debit and a 5-minute `quoteToken` | 200 |
| `POST /api/v1/send-money` (header `Idempotency-Key`) | Send Money | 200 COMPLETED / 202 PROCESSING (422 FAILED) |
| `GET /api/v1/send-money/{txnId}` | Current state | 200 |
| `GET /api/v1/admin/reconciliation?from=&to=` | Records vs ledger postings | 200 |
| `POST /api/v1/wallets` *(profile `test`)* | Register a wallet | 201 (200 on identical replay) |
| `POST /api/v1/wallets/{msisdn}/fund` *(profile `test`, header `Idempotency-Key`)* | Cash-in from issuance | 200 |
| `GET /api/v1/wallets/{msisdn}/balance` | Balance from the ledger | 200 |

Compose runs the service with `SPRING_PROFILES_ACTIVE=test`, so the support endpoints are on.

### Example session

```bash
API=http://localhost:8080
KEY='X-API-Key: local-dev-api-key'
JSON='Content-Type: application/json'

# 1. Register two wallets (KYC tier 1)
curl -s -X POST $API/api/v1/wallets -H "$KEY" -H "$JSON" \
  -d '{"msisdn":"01711000001","holderName":"Rahim Uddin","kycTier":1}'
curl -s -X POST $API/api/v1/wallets -H "$KEY" -H "$JSON" \
  -d '{"msisdn":"01811000002","holderName":"Karim Hossain","kycTier":1}'

# 2. Fund the sender with 5,000.00 BDT (idempotent per Idempotency-Key)
curl -s -X POST $API/api/v1/wallets/01711000001/fund -H "$KEY" -H "$JSON" \
  -H 'Idempotency-Key: fund-rahim-1' -d '{"amount":500000}'

# 3. Quote 1,000.00 BDT -> fee 500, VAT 65, commission 87, totalDebit 100500
curl -s -X POST $API/api/v1/send-money/quote -H "$KEY" -H "$JSON" \
  -d '{"senderMsisdn":"01711000001","receiverMsisdn":"01811000002","amount":100000,"currency":"BDT"}'

# 4. Send (repeat the same command: same result, no second debit)
curl -s -i -X POST $API/api/v1/send-money -H "$KEY" -H "$JSON" \
  -H 'Idempotency-Key: 7f9c2ba4-e88f-4d2a-9d3b-1c5e8a7f6b21' \
  -d '{"senderMsisdn":"01711000001","receiverMsisdn":"01811000002","amount":100000,"currency":"BDT","reference":"Rent"}'
#    -> 200 {"txnId":"…","status":"COMPLETED","amount":100000,"fee":500,"vat":65,"commission":87,"totalDebit":100500,"completedAt":"…"}

# 5. Status
curl -s $API/api/v1/send-money/<txnId> -H "$KEY"

# 6. Balance (posted 399500 after funding 500000 and sending 100500)
curl -s $API/api/v1/wallets/01711000001/balance -H "$KEY"

# 7. Failure demo with the stub ledger: 24,999.99 BDT -> 422 INSUFFICIENT_FUNDS (with txnId)
curl -s -i -X POST $API/api/v1/send-money -H "$KEY" -H "$JSON" \
  -H 'Idempotency-Key: demo-insufficient-1' \
  -d '{"senderMsisdn":"01711000001","receiverMsisdn":"01811000002","amount":2499999,"currency":"BDT"}'

# 8. Reconciliation of the last hour (UTC). The WireMock stub keeps no state, so its posting look-up always
#    answers NOT_FOUND: every COMPLETED row is reported as ALERT_RAISED and nothing is changed. Run it with
#    the full profile (real ledger-service) to see real results, including the "ledger wins" flip.
curl -s "$API/api/v1/admin/reconciliation?from=$(date -u -v-1H +%FT%TZ 2>/dev/null || date -u -d '-1 hour' +%FT%TZ)&to=$(date -u +%FT%TZ)" -H "$KEY"
```

Events land in the demo queue `audit.send-money` (RabbitMQ management UI → Queues → Get messages).

## Ledger wire contract (used by the client and the stub)

Authoritative contract: `../ledger-service/openapi/ledger-api.yaml` (spec 7.2). Summary:

| Call | Responses |
| --- | --- |
| `POST /internal/v1/postings` `{postingId, product, userData64, legs:[{debit, credit, amount, code}]}` | 200 `{postingId, status:"POSTED", replay, timestamp}` · 422 problem `code` `INSUFFICIENT_FUNDS` / `ACCOUNT_NOT_FOUND` / `PREVIOUSLY_REJECTED`, `postingStatus:"REJECTED"`, `legIndex` · 409 `POSTING_CONFLICT` · 503 `LEDGER_TIMEOUT` / `OVERLOADED` (`postingStatus:"UNKNOWN"`, `Retry-After`) |
| `GET /internal/v1/postings/{postingId}?legs=n` | 200 `{postingId, status:"POSTED", timestamp}` (ledger timestamp in ns) or `{postingId, status:"NOT_FOUND"}` |
| `POST /internal/v1/accounts` `{accountId, code, flags, userData64}` | 201 `{accountId, status:"CREATED"}` · 200 `EXISTS` (identical) · 409 `ACCOUNT_CONFLICT` |
| `GET /internal/v1/accounts/{id}/balance` | 200 `{accountId, debitsPosted, creditsPosted, debitsPending, creditsPending, available}` · 404 |
| `POST /internal/v1/fundings` `{fundingId, accountId, amount}` *(ledger profile `test`)* | 200 `{postingId, status:"POSTED", replay, timestamp}` (the stub also echoes `fundingId`) · 422 `ACCOUNT_NOT_FOUND` |
| `GET /actuator/health/readiness` | 200 when TigerBeetle is reachable |

## Configuration

All settings live in `src/main/resources/application.properties` (property names as in spec 12).
Environment variables override the container defaults.

| Property | Default | Env override | Meaning |
| --- | --- | --- | --- |
| `spring.datasource.url` | `jdbc:postgresql://postgres:5432/txn_db?reWriteBatchedInserts=true` | `TXN_DB_URL` | PostgreSQL |
| `spring.datasource.password` | — (required) | `TXN_DB_PASSWORD` | DB password |
| `spring.datasource.hikari.maximum-pool-size` | `20` | | P6: fixed pool, `connection-timeout` 250 ms |
| `spring.rabbitmq.host` / `username` / `password` | `rabbitmq` / — / — | `RABBIT_HOST`, `RABBIT_USER`, `RABBIT_PASSWORD` | Broker |
| `poc.ledger.base-url` | `http://ledger:8081` | `LEDGER_BASE_URL` | Ledger (spec 12 uses HAProxy `http://haproxy:8090`) |
| `poc.ledger.connect-timeout` / `read-timeout` / `total-budget` | `100ms` / `1200ms` / `3s` | | Deadline hierarchy (spec 8.3) |
| `poc.ledger.max-retries`, `poc.ledger.retry.*` | `2`; `50ms`, ×`2`, `20ms` jitter | | Retry on 503 / I/O / timeout only |
| `poc.ledger.concurrency-limit` | `512` | | Ledger bulkhead (P10) |
| `poc.api.send-money-concurrency-limit` | `1024` | | Posting endpoint bulkhead → 503 + `Retry-After: 1` |
| `poc.quote.ttl` / `poc.quote.signing-key` | `5m` / — (required) | `QUOTE_SIGNING_KEY` | Quote token |
| `poc.security.api-key` | — (required) | `API_KEY` | `X-API-Key` value |
| `poc.business.zone` | `Asia/Dhaka` | | Business date for limits |
| `poc.cache.wallet.ttl` / `max-size`, `poc.cache.rules.refresh` | `10s` / `500000`, `60s` | | Caffeine caches (P8) |
| `poc.repair.*` | interval `1s`, batch `200`, lease `30s`, min-age `3s`, backoff `1s`…`60s`, alert after `10` | | Repair worker (spec 8.4) |
| `poc.events.*` | exchange `mfs.transactions`, confirm `5s`, flush `100ms` / `500`, republish `5s` / `500` / min-age `10s` | | Events (spec 9) |
| `poc.reconciliation.max-rows` / `parallelism` | `10000` / `16` | | Reconciliation (FR-08) |
| `SPRING_PROFILES_ACTIVE` | `test` in Compose | | `test` enables register / fund wallet |

Operations: `/actuator/health/liveness`, `/actuator/health/readiness` (PostgreSQL only; a RabbitMQ
outage never blocks money, F9), `/actuator/prometheus`. Logs are JSON (ECS) on the console through
an async appender, with `txnId` and `traceId` from the MDC. Traces are exported over OTLP when
`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` is set.

### Container image

`Dockerfile`: multi-stage (Gradle wrapper build on `eclipse-temurin:25-jdk`, runtime on
`eclipse-temurin:25-jre`), non-root user, port 8080, `curl` for the healthcheck, and P16 JVM flags
in `JAVA_OPTS`: `-XX:+UseZGC -XX:InitialRAMPercentage=75 -XX:MaxRAMPercentage=75 -XX:+AlwaysPreTouch`
(heap fixed at 75% of the container limit; ZGC is generational-only on JDK 25).
