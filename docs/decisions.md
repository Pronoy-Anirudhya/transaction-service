# Decisions

Choices made where the spec (`docs/Send Money POC v2 — SRS & Design Spec.md`) is silent, or where this standalone repo
adapts the monorepo layout. Each entry keeps every MUST of the spec true.

## Build and repository

| # | Decision | Why |
| --- | --- | --- |
| D1 | Single Gradle project with Kotlin DSL (`build.gradle.kts`); no version catalog and no `build-logic` convention plugin. | Standalone repo, one module; the user asked to keep the build simple. |
| D2 | Error Prone, Checkstyle, the JaCoCo 80% gate and `-Werror` (NFR-11) are **not** enforced. | User decision for the POC. |
| D3 | Root package `com.bracits.transactionservice` (spec uses `com.poc.txn`); subpackages follow spec 10. | Keeps the existing scaffold and group `com.bracits`. |
| D4 | Contracts live under `openapi/` (`openapi/transaction-api.yaml`, `openapi/events/send-money-v1.schema.json`) instead of `contracts/`, and are copied onto the classpath at build time. | Standalone repo; `GET /openapi.yaml` serves the same file. |
| D5 | `../ledger-service/openapi/ledger-api.yaml` appeared while this service was being built; it is the authoritative ledger contract and the client follows it (problem+json errors with numeric `status` and `postingStatus`, 422 `PREVIOUSLY_REJECTED`, 503 `OVERLOADED`, funding answers with `postingId`). | Brief, section 0. |
| D6 | Spring Boot 4.1.1 pins Spring Framework 7.0.9; it already has `@ConcurrencyLimit(policy = REJECT)` and `RetryPolicy.Builder.timeout(..)`, used for the bulkheads and the 3 s total ledger budget. | Verified against the jars. |
| D7 | Mapper classes for every model conversion and constants classes instead of string literals (per layer: `DomainConstants`, `ApiConstants`, `PropertyConstants`, …). | User coding rules. |

## Behaviour

| # | Decision | Why |
| --- | --- | --- |
| B1 | The insert of DB transaction #1 sets `next_check_at = now()`; the repair query also requires `created_at < now() - 3 s`. | Rows left behind by a crash between DB tx #1 and the ledger call (F5) are found by repair, while in-flight requests are not touched. |
| B2 | Requests rejected before the insert (rules, pricing, quote) write no row and emit no event. | Spec 5 step 3: "no write"; events are for final transaction records. |
| B3 | On any pre-insert rejection, the service looks up `(sender, Idempotency-Key)`; if a row exists, the duplicate logic of step 6.2 applies instead. | FR-03 (replay returns the stored result) holds even if the rules changed since; the happy path keeps its 3 statements. |
| B4 | The quote checks BR-01 and BR-02 only (no limit read). The quote token is `base64url(payload).base64url(HMAC-SHA256)`; a malformed token or bad signature → 400 `INVALID_QUOTE_TOKEN`; expired, other sender/receiver/amount, or a different fee → 409 `QUOTE_CHANGED`. | FR-01 says "no writes"; 409 is reserved for a genuine quote change. |
| B5 | Ledger 422 `ACCOUNT_NOT_FOUND` → `WALLET_NOT_FOUND`; any other unknown 422 code → `LEDGER_REJECTED`. | Spec failure-code list has no account code. |
| B6 | Ledger 409 / 500 → row stays INITIATED with a recheck (`next_check_at = now() + 2 s`), client gets 202, error log as the alert. | Spec 8.3 outcome table. |
| B7 | A retry is attempted only while the remaining total budget (3 s) is at least one read timeout. | Keeps the deadline hierarchy strict: 1.2 s + 1.2 s + backoff fits, a third timed-out attempt would not. |
| B8 | If the ledger-port bulkhead rejects after DB tx #1, the row stays INITIATED (recheck) and the client gets 202. The posting-endpoint bulkhead rejects before any write with 503 + `Retry-After: 1`. | Load is shed at the edge; a reserved row is always resolved by repair. |
| B9 | The republisher reuses `next_check_at` as its lease column on final rows. | No new column; the column is unused once a row is final and is outside the `smt_in_doubt` partial index. |
| B10 | Reconciliation scans `send_money_txn` by `txn_id` range (txnIds are time-ordered) with a per-call row cap and a `truncated` flag. | No extra index on the write path (P14). |
| B11 | Funding ID = UUIDv5(funding namespace, walletId + Idempotency-Key) with the low 8 bits cleared; the fund endpoint requires `Idempotency-Key`. | Every endpoint must be idempotent (NFR-06); the ledger dedupes by transfer ID. |
| B12 | Wallet registration is idempotent: the same MSISDN with identical fields re-ensures the ledger account and returns the stored wallet; different fields → 409 `MSISDN_EXISTS`. | Safe retry after a failure between the DB insert and the ledger call. |
| B13 | Balance: `posted = creditsPosted − debitsPosted`, `pending = creditsPending − debitsPending`, `available` as returned by the ledger. | Customer wallets are credit-normal. |
| B14 | A sender wallet that is not of type CUSTOMER is reported as `WALLET_NOT_FOUND`; a non-CUSTOMER receiver as `RECEIVER_NOT_ALLOWED`. | BR-01; no dedicated code for the sender case. |
| B15 | Support endpoints (register, fund) are active under the Spring profile `test`; balance is always on. | Spec 7.1 marks only register/fund as test profile. |
| B16 | The event is handed to a virtual thread right after finalisation, so the response never waits for the broker. | Spec 9 rule 2. |

## Domain (pricing, IDs)

| # | Decision | Why |
| --- | --- | --- |
| P1 | PERCENT fees: `amount × fee_value / 10,000` rounded half-up (integer quotient/remainder). | Spec gives the VAT rounding only; half-up is the conventional choice and matches VAT. |
| P2 | Clamp order: raise to `fee_min`, then cap at `fee_max` (a misconfigured `fee_min > fee_max` yields `fee_max`). | Deterministic; never charges above the maximum. |
| P3 | The first slab (by product, tier, `min_amount`) covering the amount wins; overlapping slabs are a configuration error, not detected. | Simplest; seed data has no overlaps. |
| P4 | BR-09 is applied per part: fee income, VAT and commission legs are each omitted when zero; leg indexes are positions in the posted batch. `LegPlanner.legCount` uses the same rule for posting look-ups. | Deterministic from the stored pricing, so every resend is identical. |
| P5 | txnIds carry no RFC 9562 version/variant bits (48-bit ms + 72 random + 8 zero bits, exactly as spec 6.3). Ledger account IDs of registered wallets use the same generator. | Spec layout; PostgreSQL `uuid` accepts any 128 bits and compares bytewise, so range scans by time work. |
| P6 | Funding ID name = `walletId + ":" + Idempotency-Key`, UUIDv5, low byte cleared. | See B11. |

## Adapters and wiring

| # | Decision | Why |
| --- | --- | --- |
| W1 | `HttpLedgerClient` is not `final` and uses `@Proxyable(TARGET_CLASS)`: Spring 7.0.9 resolves `@ConcurrencyLimit` on the invoked method, which a JDK interface proxy reports as `LedgerPort.post` (no annotation). Same reason `SendMoneyService` is a non-final class without an interface. | The bulkhead must work. |
| W2 | Ledger retries resend the same serialized bytes; only 503 / I/O / connect or read timeout are retried; `RetryPolicy.timeout(3 s)` plus an explicit "remaining budget ≥ read timeout" check (B7). Jitter in 7.0.9 only lengthens delays (50–70 ms, then 100–140 ms). | Spec 8.3. |
| W3 | JDBC repositories are `@Component` (not `@Repository`): Boot 4's persistence-exception-translation proxy cannot subclass the `final` repositories, and `JdbcClient` already throws `DataAccessException`. | Startup would fail otherwise. |
| W4 | Status literals (`'INITIATED'`) stay inline in SQL so the planner can use the partial indexes. | P14. |
| W5 | `markEventsPublished` only touches rows still unmarked (`AND event_published_at IS NULL`). | Avoids rewriting rows on duplicate acks. |
| W6 | The rule cache reloads on virtual threads and keeps the old list if a reload fails. | The request path never waits for, or fails because of, a rule refresh. |
| W7 | The event-send executor is a small wrapper, not an `Executor` bean (an `Executor` bean would disable Boot's `applicationTaskExecutor`). | Keep Boot defaults intact. |
| W8 | RabbitMQ topology is declared in the background at `ApplicationReadyEvent` and never fails start-up. | F9: a broker outage must not stop the money path. |
| W9 | Event payload is serialized with `@JsonInclude(ALWAYS)` and ISO-8601 `occurredAt`, independent of global Jackson settings. | The JSON Schema requires `null` fields to be present. |
| W10 | A posting look-up returns the ledger timestamp when POSTED (`{"status":"POSTED","timestamp":…}`); "ledger wins" stores it as `ledger_ts`, or NULL if the ledger did not supply it. | Spec 8.5 needs to finalise from ledger truth. |
| W11 | The repair worker, flusher and republisher run via `@Scheduled` with duration strings (`1s`, `100ms`), which Spring 7.0.9 parses. | Spec 12 property values. |

## Ledger contract alignment

| # | Decision | Why |
| --- | --- | --- |
| L1 | 422 `PREVIOUSLY_REJECTED` → `LEDGER_REJECTED` (definitive FAILED); unknown 422 codes likewise. | The ledger does not repeat the original reason; any 422 is definitive per the contract. |
| L2 | `Retry-After` from the ledger is not slept on inside the request: the spec 8.3 backoff (50 ms ×2 ± 20 ms) and the 3 s total budget take precedence. After the budget the posting is `Unknown` → 202, and repair (backoff from 1 s) resends later. | A 1 s sleep would leave no room for a 1.2 s read timeout within 3 s (deadline hierarchy). |
| L3 | A 400 from the ledger (validation) is treated as `Unknown(LEDGER_ERROR)` with an alert, never as a rejection. | The request is built by this service, so a 400 is a bug; NFR-03 forbids FAILED without a definitive 422. |
| L5 | The WireMock stub's posting look-up always answers NOT_FOUND. A stateless stub cannot know what was posted; answering POSTED would make reconciliation flip deliberately failed demo transactions ("ledger wins"), re-count limits and emit corrective events. NOT_FOUND can only raise alerts. | Demo safety; real reconciliation runs with the `full` profile. |
| L6 | The stub gets its own Compose healthcheck on port 8081 (the image's built-in check probes 8080). | Found in the smoke run. |
| L4 | Funding rejections other than `ACCOUNT_NOT_FOUND` surface as 503 to the caller (test-profile endpoint only). | The accounts port has no rejection type; funding from issuance cannot be short of funds. |
