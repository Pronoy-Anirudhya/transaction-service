# Send Money POC v2 — SRS & Design Spec

Oct 7, 2026 · @Anirudhya Sarker

**This spec defines a two-service Send Money POC built the way a production MFS builds its money path.** `transaction-service` (Java, PostgreSQL) runs the Send Money business rules and calls `ledger-service` (Java, TigerBeetle) synchronously. The ledger posts all legs (principal, fee, VAT, commission) in one atomic linked batch. After the outcome is final, the transaction service publishes an event to RabbitMQ. There is no 2PC, no outbox table and no Go.

**Instructions to the implementing agent**

1. This document is the single source of truth. **MUST**, **SHOULD** and **MAY** follow RFC 2119. Where this spec is silent, choose the simplest option that keeps every MUST true, and record the choice in `docs/decisions.md`.
2. Build exactly two deployable services: `transaction-service` and `ledger-service`. Don't add services, frameworks or stores not named here.
3. **Don't** introduce 2PC, XA, sagas, an outbox table, JPA/Hibernate on the request path, or Go.
4. Implement in the milestone order of section 13. Every milestone ends with its tests green; don't start the next one with a red build.
5. Section 11 (performance) is **mandatory**, not advice. Measure with the k6 scenarios in section 13 before claiming a milestone done.
6. Money is always `long` minor units (poisha). **Never** `double`, `float` or `BigDecimal` arithmetic on amounts. `BigInteger` only at the TigerBeetle client boundary.
7. Every endpoint, internal or external, MUST be idempotent as specified. Write the idempotency tests first.

## 1. Scope, assumptions and success criteria

**In scope**

- Send Money, wallet to wallet: quote, confirm, status.
- Fee charged to the sender. VAT is carved out of the fee, and a configurable commission is carved out of the net fee.
- Per-tier transaction, daily and monthly limits.
- Synchronous, atomic ledger posting in TigerBeetle, with complete failure handling.
- `SendMoneyCompleted` / `SendMoneyFailed` events published to RabbitMQ.
- Repair worker (in-doubt transactions), event republisher, reconciliation endpoint.
- **Support endpoints only for testing:** register wallet, fund wallet (simulated cash-in), balance.

**Out of scope:** API gateway, authentication, PIN/HSM, KYC, risk engine, event consumers (notification, history, AML…), other products, UI. The request carries the sender's MSISDN directly, protected by a static API key. Each of these slots in later without changing this design (the reference-flows doc shows where).

**Assumptions (change them here if wrong)**

| # | Assumption |
| --- | --- |
| A1 | **Commission** is a configurable share of the net fee (fee minus VAT), credited to a *Commission Payable* account, for example for a channel or distribution partner. A rate of 0 disables it. |
| A2 | The fee is **VAT-inclusive**; VAT rate 15% (configurable, in basis points). |
| A3 | Limits are enforced in PostgreSQL, per sender per business day and month in Asia/Dhaka. TigerBeetle limit accounts are a later option. |
| A4 | Single currency, BDT; TigerBeetle ledger `1`, asset scale 2 (poisha). |
| A5 | The POC runs a single-replica TigerBeetle; production uses 6 replicas. No code changes between the two. |

**Success criteria**

1. **Money conservation:** the sum of all customer, fee income, VAT payable and commission payable balances equals the e-money issuance account's debit balance, at all times.
2. **Record = ledger:** every COMPLETED transaction has all of its legs posted in TigerBeetle; every FAILED transaction has none. Verified by the reconciliation endpoint after every test run.
3. **No stuck transactions:** nothing stays INITIATED for more than 60 s once dependencies are healthy.
4. **Events:** every final transaction's event is delivered at least once (republisher), and consumers can dedupe by `message-id`.
5. **Throughput (calibrate on first run):** ≥ 1,500 Send Money/s with one instance of each service, and ≥ 2,700/s (near-linear) with two `transaction-service` instances, at p95 ≤ 60 ms and p99 ≤ 150 ms server-side, on the reference host (16 vCPU, 32 GB, NVMe) running the whole stack.

## 2. Architecture

&#91;embedded content: POC v2 architecture · 2 services, 3 stores\]

`transaction-service` is the hub: it owns the business rules and the transaction record, and it is the only caller of `ledger-service`. `ledger-service` is a thin, product-agnostic gateway to TigerBeetle. Both are stateless and run as two instances behind HAProxy. The dashed arrow is the only asynchronous step: events go to RabbitMQ after the outcome is committed.

| Component | Owns | Store |
| --- | --- | --- |
| `transaction-service` | Send Money API, rules, fee/VAT/commission, limits, idempotency, transaction record, repair, events | PostgreSQL `txn_db` |
| `ledger-service` | Chart of accounts, atomic multi-leg postings, balances | TigerBeetle (no other database) |
| RabbitMQ | Event distribution to future consumers | Quorum queues |

## 3. Functional requirements and business rules

**Functional requirements**

| ID | Requirement | Service |
| --- | --- | --- |
| FR-01 | **Quote:** given sender, receiver and amount, return the receiver's masked name, fee, total debit and a signed `quoteToken` valid for 5 minutes. No writes. | transaction |
| FR-02 | **Send Money:** validate, price, reserve limits, post atomically to the ledger, record the outcome and return the final status in the same HTTP response. | transaction → ledger |
| FR-03 | **Idempotency:** `Idempotency-Key` header (≤ 64 chars) is required. The same key from the same sender with the same body returns the stored result; a different body returns 409. | transaction |
| FR-04 | **Status:** `GET /send-money/{txnId}` returns the current state. | transaction |
| FR-05 | **Events:** publish `SendMoneyCompleted` or `SendMoneyFailed` for every final transaction. | transaction → RabbitMQ |
| FR-06 | **Repair:** resolve every in-doubt transaction (ledger outcome unknown) from the ledger's answer, without manual action. | transaction |
| FR-07 | **Republish:** re-send events not yet confirmed by the broker. | transaction |
| FR-08 | **Reconciliation:** report any mismatch between transaction records and ledger postings for a time window. | transaction + ledger |
| FR-09 | **Support (test profile):** register wallet, fund wallet from the issuance account, read balance. | both |

**Business rules**

| ID | Rule |
| --- | --- |
| BR-01 | Sender ≠ receiver. Both wallets exist, are ACTIVE and of type CUSTOMER. |
| BR-02 | Amount within the sender tier's per-transaction minimum and maximum (`limit_rule`). |
| BR-03 | Sender's daily and monthly **amount** and **count** limits are not exceeded, including this transaction (`limit_rule`, `wallet_limit_usage`). |
| BR-04 | **Fee** comes from the matching `fee_rule` slab (product, tier, amount range): `FLAT` (minor units) or `PERCENT` (basis points), then clamped to `[fee_min, fee_max]`. |
| BR-05 | **VAT** = round half-up of `fee × vat_bps / (10,000 + vat_bps)` (the fee is VAT-inclusive). |
| BR-06 | **Commission** = floor of `(fee − VAT) × commission_bps / 10,000`. |
| BR-07 | **Fee income** = `fee − VAT − commission`. The three parts always add up to the fee exactly; no poisha is lost or created. |
| BR-08 | The sender is debited **amount + fee**. Sufficient available balance is checked atomically by the ledger, not by the transaction service. |
| BR-09 | Legs with a zero amount (no fee, or zero commission) are omitted from the posting. |

**Worked example:** Rahim sends 1,000.00 BDT. The slab fee is 5.00 BDT, VAT 15% (1,500 bps), commission 20% (2,000 bps).

| Item | Calculation | Poisha |
| --- | --- | --- |
| Amount | — | 100,000 |
| Fee | slab | 500 |
| VAT | 500 × 1,500 / 11,500 = 65.2 → half-up | 65 |
| Commission | (500 − 65) × 2,000 / 10,000 = 87.0 → floor | 87 |
| Fee income | 500 − 65 − 87 | 348 |
| Sender debited | 100,000 + 500 | 100,500 |

**Example `fee_rule` and `limit_rule` seed data (placeholders for the POC):** fee 0 for 1–100 BDT; 5 BDT flat for 100.01–25,000 BDT. Tier 1: per transaction 10–25,000 BDT, daily 50,000 BDT / 50 transactions, monthly 300,000 BDT / 200 transactions.

## 4. Non-functional requirements

| ID | Area | Requirement |
| --- | --- | --- |
| NFR-01 | Throughput | Section 1, criterion 5. Throughput MUST scale near-linearly by adding `transaction-service` and `ledger-service` instances (both stateless). |
| NFR-02 | Latency | Server-side p95 ≤ 60 ms, p99 ≤ 150 ms for Send Money at target load. Quote p95 ≤ 10 ms. |
| NFR-03 | Consistency | The ledger is the source of truth for money. A transaction record MUST NOT be marked FAILED without a definitive ledger rejection. |
| NFR-04 | Durability | A 200 COMPLETED response MUST only be sent after TigerBeetle has acknowledged the posting (replicated and applied). |
| NFR-05 | Availability | Any single `transaction-service` or `ledger-service` instance may die at any moment without losing or duplicating money. |
| NFR-06 | Idempotency | Every endpoint and every ledger posting is safe to retry indefinitely. |
| NFR-07 | Backpressure | Under overload, services MUST shed load quickly with 503 + `Retry-After`, never queue without bound. |
| NFR-08 | Observability | Every request has one trace across both services and into the RabbitMQ message headers. RED metrics per endpoint; business counters per outcome (section 12). |
| NFR-09 | Security (POC level) | Public API behind a static API key; `/internal/**` reachable only on the private network; MSISDN masked in logs; no secrets in the repository. |
| NFR-10 | Money | Integer poisha only; ISO 4217 currency; UTC timestamps; business date in Asia/Dhaka. |
| NFR-11 | Code quality | Hexagonal layout; ≥ 80% line coverage of domain and application code; Error Prone + Checkstyle clean; no compiler warnings. |
| NFR-12 | Versions | Java 25, Spring Boot 4.1.1, Gradle (Kotlin DSL, version catalog), PostgreSQL 18, RabbitMQ 4.x, TigerBeetle 0.17.x with the matching `tigerbeetle-java` client. |

## 5. Send Money flow

&#91;embedded content: Send Money happy path · synchronous until the response, then the event\]

**Algorithm for `POST /api/v1/send-money`.** The request path makes **2 short PostgreSQL transactions and 1 ledger call**. Everything else is in memory or after the response.

1. **Validate input** (Bean Validation): MSISDN format, amount > 0, currency BDT, `Idempotency-Key` present. Compute `request_hash` = SHA-256 of the canonical body.
2. **Load wallets** for sender and receiver from `WalletCache` (Caffeine; a miss reads PostgreSQL).
3. **Run the rule chain** BR-01, BR-02 in memory. Any failure → 422 with no write.
4. **Price** with `FeeCalculator` from cached `fee_rule`s → `Pricing{fee, vat, commission, feeIncome}` (BR-04 to BR-07).
5. **Generate `txnId`** (section 6, ID scheme).
6. **DB transaction #1, reserve:**
   1. `INSERT INTO send_money_txn (…, status 'INITIATED') ON CONFLICT (sender_wallet_id, client_ref) DO NOTHING RETURNING txn_id`.
   2. No row returned → duplicate. Commit, load the existing row: same `request_hash` → return its stored outcome (INITIATED → 202 PROCESSING); different hash → 409.
   3. Otherwise run the conditional limit update (section 6, `wallet_limit_usage`). 0 rows → **roll back** (the insert disappears) → 422 `LIMIT_EXCEEDED`.
   4. Commit.
7. **Ledger call** `POST /internal/v1/postings` with the legs (section 6), the deadline hierarchy and retry policy of section 8.
8. **Finalise** by outcome:
   - **POSTED** → `UPDATE send_money_txn SET status='COMPLETED', ledger_ts=?, completed_at=now() WHERE txn_id=? AND status='INITIATED'` (single statement, auto-commit).
   - **REJECTED** (definitive) → **one statement** (CTE) that marks the row FAILED with the reason **and** releases the limit usage (section 6).
   - **UNKNOWN** (retries exhausted) → leave INITIATED, set `next_check_at = now() + 2 s`; the repair worker resolves it (section 8).
9. **Respond:** 200 COMPLETED, 422 FAILED + reason, or 202 PROCESSING (with `txnId` to poll).
10. **After the response** (virtual thread, never blocks the response): publish the event to RabbitMQ with publisher confirms; on ack, mark `event_published_at` (batched). Section 9.

**Why this order**

- Cheapest rejections first: in-memory rules, then limits in PostgreSQL, then the ledger.
- **The row is durable before the ledger is called**, so no posting can ever exist without a record of it.
- **Limits are reserved before posting and released only on a definitive rejection**, so two concurrent sends can't both slip under a limit.
- **Balance is never checked by the transaction service.** TigerBeetle checks it atomically with the debit, so there's no check-then-act race.

## 6. Data design

### 6.1 Transaction database (`txn_db`, PostgreSQL 18, owned by `transaction-service`, migrated with Flyway)

```sql
CREATE TABLE wallet (
  wallet_id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  msisdn             varchar(15)  NOT NULL UNIQUE,
  holder_name        varchar(100) NOT NULL,
  wallet_type        varchar(10)  NOT NULL CHECK (wallet_type IN ('CUSTOMER','SYSTEM')),
  status             varchar(10)  NOT NULL CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
  kyc_tier           smallint     NOT NULL,
  ledger_account_id  uuid         NOT NULL UNIQUE,      -- = TigerBeetle account.id
  created_at         timestamptz  NOT NULL DEFAULT now(),
  updated_at         timestamptz  NOT NULL DEFAULT now()
);

-- one row per wallet, created with the wallet; day/month counters reset in-place
CREATE TABLE wallet_limit_usage (
  wallet_id     bigint PRIMARY KEY REFERENCES wallet(wallet_id),
  day           date   NOT NULL,
  day_amount    bigint NOT NULL DEFAULT 0,
  day_count     int    NOT NULL DEFAULT 0,
  month         date   NOT NULL,                       -- first day of month
  month_amount  bigint NOT NULL DEFAULT 0,
  month_count   int    NOT NULL DEFAULT 0
) WITH (fillfactor = 70);                              -- room for HOT updates

CREATE TABLE limit_rule (
  product        varchar(20) NOT NULL,
  kyc_tier       smallint    NOT NULL,
  per_txn_min    bigint NOT NULL, per_txn_max    bigint NOT NULL,
  daily_amount   bigint NOT NULL, daily_count    int    NOT NULL,
  monthly_amount bigint NOT NULL, monthly_count  int    NOT NULL,
  PRIMARY KEY (product, kyc_tier)
);

CREATE TABLE fee_rule (
  rule_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product        varchar(20) NOT NULL,
  kyc_tier       smallint    NOT NULL,
  min_amount     bigint NOT NULL, max_amount bigint NOT NULL,   -- inclusive slab
  fee_type       varchar(7)  NOT NULL CHECK (fee_type IN ('FLAT','PERCENT')),
  fee_value      bigint NOT NULL,                  -- poisha if FLAT, bps if PERCENT
  fee_min        bigint NOT NULL DEFAULT 0, fee_max bigint,
  vat_bps        int    NOT NULL,
  commission_bps int    NOT NULL DEFAULT 0,
  active         boolean NOT NULL DEFAULT true
);

CREATE TABLE send_money_txn (
  txn_id             uuid        PRIMARY KEY,         -- time-ordered; = TB transfer.user_data_128
  client_ref         varchar(64) NOT NULL,            -- Idempotency-Key
  request_hash       bytea       NOT NULL,
  sender_wallet_id   bigint      NOT NULL REFERENCES wallet(wallet_id),
  receiver_wallet_id bigint      NOT NULL REFERENCES wallet(wallet_id),
  amount             bigint      NOT NULL CHECK (amount > 0),
  fee                bigint      NOT NULL, vat bigint NOT NULL,
  commission         bigint      NOT NULL, fee_income bigint NOT NULL,
  currency           char(3)     NOT NULL DEFAULT 'BDT',
  reference          varchar(50),
  business_date      date        NOT NULL,            -- Asia/Dhaka, for limit release
  status             varchar(10) NOT NULL CHECK (status IN ('INITIATED','COMPLETED','FAILED')),
  failure_code       varchar(32),
  ledger_attempts    smallint    NOT NULL DEFAULT 0,
  next_check_at      timestamptz,
  ledger_ts          bigint,                          -- TigerBeetle transfer timestamp (ns)
  created_at         timestamptz NOT NULL DEFAULT now(),
  completed_at       timestamptz,
  event_published_at timestamptz,
  UNIQUE (sender_wallet_id, client_ref)
) WITH (fillfactor = 80);

CREATE INDEX smt_in_doubt   ON send_money_txn (next_check_at) WHERE status = 'INITIATED';
CREATE INDEX smt_unpublished ON send_money_txn (completed_at)
  WHERE status <> 'INITIATED' AND event_published_at IS NULL;
```

The two partial indexes stay tiny: they only contain rows still in flight. Keep other indexes out of the write path; add read indexes (for example sender history) to a replica or a later read model.

**Limit reservation: one statement, one row lock per sender**

```sql
UPDATE wallet_limit_usage u SET
  day_amount   = CASE WHEN u.day   = :today THEN u.day_amount   ELSE 0 END + :amt,
  day_count    = CASE WHEN u.day   = :today THEN u.day_count    ELSE 0 END + 1,
  month_amount = CASE WHEN u.month = :month THEN u.month_amount ELSE 0 END + :amt,
  month_count  = CASE WHEN u.month = :month THEN u.month_count  ELSE 0 END + 1,
  day = :today, month = :month
WHERE u.wallet_id = :sender
  AND CASE WHEN u.day   = :today THEN u.day_amount   ELSE 0 END + :amt <= :dailyAmount
  AND CASE WHEN u.day   = :today THEN u.day_count    ELSE 0 END + 1    <= :dailyCount
  AND CASE WHEN u.month = :month THEN u.month_amount ELSE 0 END + :amt <= :monthlyAmount
  AND CASE WHEN u.month = :month THEN u.month_count  ELSE 0 END + 1    <= :monthlyCount;
```

**Failure finalisation: mark FAILED and release in one round trip.** The release only subtracts from a counter if it still belongs to the transaction's day or month, so a midnight boundary can't corrupt the next day.

```sql
WITH t AS (
  UPDATE send_money_txn SET status='FAILED', failure_code=:code, completed_at=now()
   WHERE txn_id = :txnId AND status = 'INITIATED'
  RETURNING sender_wallet_id, amount, business_date
)
UPDATE wallet_limit_usage u SET
  day_amount   = u.day_amount   - CASE WHEN u.day   = t.business_date THEN t.amount ELSE 0 END,
  day_count    = u.day_count    - CASE WHEN u.day   = t.business_date THEN 1 ELSE 0 END,
  month_amount = u.month_amount - CASE WHEN u.month = date_trunc('month', t.business_date)::date THEN t.amount ELSE 0 END,
  month_count  = u.month_count  - CASE WHEN u.month = date_trunc('month', t.business_date)::date THEN 1 ELSE 0 END
FROM t WHERE u.wallet_id = t.sender_wallet_id;
```

### 6.2 TigerBeetle model (owned by `ledger-service`)

**Ledger `1`:** BDT e-money, amounts in poisha.

| Account | `code` | `flags` | `id` | `user_data_64` |
| --- | --- | --- | --- | --- |
| Customer wallet | 100 | `DEBITS_MUST_NOT_EXCEED_CREDITS` | `wallet.ledger_account_id` | `wallet.wallet_id` |
| Fee income | 200 | none | fixed `…000000c8` | 0 |
| VAT payable | 210 | none | fixed `…000000d2` | 0 |
| Commission payable | 220 | none | fixed `…000000dc` | 0 |
| E-money issuance | 900 | none | fixed `…00000384` | 0 |

The fixed IDs are the code in hex in the low bytes of an otherwise-zero 128-bit ID. `ledger-service` creates them at start-up (idempotently: `exists` is fine). Both services read them from configuration.

**Send Money legs: one linked batch**

| Leg | Debit | Credit | Amount | `code` |
| --- | --- | --- | --- | --- |
| 1 | Sender wallet | Receiver wallet | amount | 10 Principal |
| 2 | Sender wallet | Fee income | fee\_income | 11 Fee |
| 3 | Sender wallet | VAT payable | vat | 12 VAT |
| 4 | Sender wallet | Commission payable | commission | 13 Commission |

Every leg except the last carries `LINKED`. Transfer fields: `user_data_128` = `txnId`, `user_data_64` = sender `wallet_id`, `user_data_32` = 1 (product SEND\_MONEY), `timeout` = 0, `ledger` = 1. Funding uses code 1 (issuance → wallet), single transfer.

### 6.3 ID scheme

- `txnId` is 128-bit and time-ordered: a 48-bit Unix-millisecond timestamp, then 72 random bits (`ThreadLocalRandom`), then **8 zero bits**. It's stored as `uuid` in PostgreSQL and as a `UInt128` in TigerBeetle. Time ordering keeps B-tree and LSM inserts sequential.
- **Transfer ID of leg *n*** = `txnId | n` (n = 1–4). This is deterministic, so every retry, from any instance, at any time, sends identical IDs. That is what makes the ledger call idempotent.

### 6.4 How the two stores refer to each other

There are no cross-store foreign keys. The references are IDs on both sides, checked by reconciliation (FR-08).

| PostgreSQL | TigerBeetle | Direction of truth |
| --- | --- | --- |
| `wallet.ledger_account_id` | `account.id` | PostgreSQL assigns it, TigerBeetle stores it |
| `wallet.wallet_id` | `account.user_data_64` | Back-reference for reports and reconciliation |
| `send_money_txn.txn_id` | `transfer.user_data_128`, and `transfer.id = txn_id \| leg` | **TigerBeetle is the truth for money**; the row follows the ledger |
| `send_money_txn.ledger_ts` | `transfer.timestamp` | Copied from the posting result |
| — | Account balances | **Only in TigerBeetle.** PostgreSQL never stores a balance. |

## 7. API contracts

All APIs are JSON over HTTP/1.1 keep-alive. Errors use RFC 9457 Problem Details (`application/problem+json`) with a machine-readable `code`. The OpenAPI 3.1 files in `contracts/` are authoritative; both services MUST pass contract tests against them.

### 7.1 `transaction-service` (public, behind the API key)

| Method & path | Purpose | Success | Errors |
| --- | --- | --- | --- |
| `POST /api/v1/send-money/quote` | FR-01 | 200 `{receiverName, amount, fee, vat, commission, totalDebit, quoteToken, expiresAt}` | 422 rule failures |
| `POST /api/v1/send-money` | FR-02 | **200** COMPLETED · **202** PROCESSING | 400, 409 `IDEMPOTENCY_CONFLICT`, 422 FAILED + `code`, 503 overloaded |
| `GET /api/v1/send-money/{txnId}` | FR-04 | 200 current state | 404 |
| `GET /api/v1/admin/reconciliation?from=&to=` | FR-08 | 200 `{checked, mismatches:[…]}` | — |
| `POST /api/v1/wallets` *(test profile)* | Register | 201 `{walletId, msisdn, ledgerAccountId}` | 409 MSISDN exists |
| `POST /api/v1/wallets/{msisdn}/fund` *(test profile)* | Fund from issuance | 200 | 404 |
| `GET /api/v1/wallets/{msisdn}/balance` | Balance (read through to the ledger) | 200 `{posted, pending, available}` | 404 |

**Send Money request** (header `Idempotency-Key: 7f9c…`)

```json
{"senderMsisdn":"01711000001","receiverMsisdn":"01811000002",
 "amount":100000,"currency":"BDT","reference":"Rent","quoteToken":"…optional…"}
```

**Send Money response (200)**

```json
{"txnId":"0192f5a4-…","status":"COMPLETED","amount":100000,"fee":500,"vat":65,
 "commission":87,"totalDebit":100500,"completedAt":"2026-10-07T09:14:03.211Z"}
```

If a `quoteToken` is sent, the fee MUST equal the quoted fee, or the service returns 409 `QUOTE_CHANGED`. **Failure codes:** `INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, `AMOUNT_OUT_OF_RANGE`, `SELF_TRANSFER`, `WALLET_NOT_FOUND`, `WALLET_INACTIVE`, `RECEIVER_NOT_ALLOWED`, `QUOTE_CHANGED`, `IDEMPOTENCY_CONFLICT`.

### 7.2 `ledger-service` (internal network only)

| Method & path | Purpose | Responses |
| --- | --- | --- |
| `POST /internal/v1/accounts` | Create an account `{accountId, code, flags, userData64}` | 201 created · 200 already exists (identical) · 409 exists with different fields |
| `POST /internal/v1/postings` | **Atomic multi-leg posting** | **200** POSTED · **422** REJECTED · **409** conflict · **503** ledger unavailable (outcome unknown) |
| `GET /internal/v1/postings/{postingId}?legs=n` | Look up a posting's legs | 200 `{status: POSTED \| NOT_FOUND}` |
| `GET /internal/v1/accounts/{id}/balance` | Balance | 200 `{debitsPosted, creditsPosted, debitsPending, creditsPending, available}` |
| `POST /internal/v1/fundings` *(test profile)* | Issuance → wallet | 200 POSTED |
| `GET /actuator/health/{liveness,readiness}` | Readiness = TigerBeetle reachable | — |

**Posting request**

```json
{"postingId":"0192f5a4-…","product":1,"userData64":42,
 "legs":[{"debit":"…sender","credit":"…receiver","amount":100000,"code":10},
         {"debit":"…sender","credit":"…c8","amount":348,"code":11},
         {"debit":"…sender","credit":"…d2","amount":65,"code":12},
         {"debit":"…sender","credit":"…dc","amount":87,"code":13}]}
```

**Posting responses**

```json
200 {"postingId":"…","status":"POSTED","replay":false,"timestamp":1791350858928000000}
422 {"postingId":"…","status":"REJECTED","code":"INSUFFICIENT_FUNDS","legIndex":1}
503 {"postingId":"…","status":"UNKNOWN","code":"LEDGER_TIMEOUT"}
```

`ledger-service` validates the request: 1–8 legs, every amount > 0, the low byte of `postingId` is 0, every account ID non-zero. The ledger service is **generic**: it knows nothing about Send Money, only legs and codes, so future products reuse it unchanged.

## 8. Ledger call: atomic commit and failure handling

### 8.1 What "committed" means

TigerBeetle acknowledges a `create_transfers` request only after the batch is **durably replicated and applied**. A linked chain is all-or-nothing. So `ledger-service` returns **200 POSTED only after TigerBeetle has acknowledged the whole chain**; at that moment the money has moved and cannot be lost. The transaction service treats 200 as the commit point.

### 8.2 Inside `ledger-service`

1. Validate the request, then build one `TransferBatch`: one row per leg, `id = postingId | legIndex`, `LINKED` on all but the last leg, `user_data_*` per section 6.2.
2. Call `client.createTransfersAsync(batch)` on the **single shared `Client`**, then `future.get(tbDeadline)` with `tbDeadline` = 800 ms. The async call plus `get` parks the virtual thread instead of pinning a carrier thread inside JNI.
3. Map the results. TigerBeetle returns results only for events that did not succeed; an empty result means all legs posted.

| TigerBeetle result | Meaning | Response |
| --- | --- | --- |
| (no results) | All legs created | 200 POSTED, `replay:false` |
| `exists` on every leg | Same posting already committed (a retry) | 200 POSTED, `replay:true` |
| `exceeds_credits` | Sender's available balance too low | 422 `INSUFFICIENT_FUNDS` |
| `debit_account_not_found` / `credit_account_not_found` | Wallet account missing | 422 `ACCOUNT_NOT_FOUND` |
| `linked_event_failed` | Another leg in the chain failed | Ignore; report the root-cause leg |
| `exists_with_different_*` | Same ID, different content: a bug | 409 `POSTING_CONFLICT`, error log + alert |
| Any other code | Validation bug | 500, alert |
| `get` times out or the client errors | Outcome unknown | **Fence** (below), then 503 `LEDGER_TIMEOUT` |

4. **Fence on timeout.** A timed-out request may still be sitting in the client's queue and could reach TigerBeetle later. On a timeout, `ledger-service` MUST replace its `Client` (create a new one, swap an `AtomicReference`, then close the old one). Closing the old client abandons any request it hadn't sent. After this, the outcome of the timed-out posting is fixed: it either already committed (a retry gets `exists`) or never will, so a later rejection on retry is definitive. Concurrent requests on the old client fail with 503 and are retried by their callers, which is safe because postings are idempotent.

### 8.3 Inside `transaction-service`: deadlines and retries

**Deadline hierarchy.** Each layer's timeout is shorter than the one above it, so the inner layer always gives up (and fences) before the outer one retries.

| Layer | Timeout |
| --- | --- |
| TigerBeetle `future.get` in `ledger-service` | 800 ms |
| HTTP read timeout, transaction → ledger | 1,200 ms (connect 100 ms) |
| Total ledger budget per request, retries included | 3 s |
| Client-facing request (load test / app) | 5 s |

**Retry policy** (Spring Framework 7 `RetryTemplate`): retry **only** on 503, I/O error or read timeout. At most 2 retries, 50 ms initial delay, ×2 backoff, 20 ms jitter. **Always resend the identical request** (same `postingId`, same legs). Never retry 422 or 409. Retries MAY go to a different `ledger-service` instance.

**Outcome handling**

| Ledger answer | `send_money_txn` | Limits | Client gets | Event |
| --- | --- | --- | --- | --- |
| 200 POSTED | COMPLETED | stay counted | 200 | `SendMoneyCompleted` |
| 422 REJECTED | FAILED + code | released (same statement) | 422 | `SendMoneyFailed` |
| 409 / 500 | stays INITIATED, alert | stay reserved | 202 PROCESSING | later, via repair |
| Still 503 / timeout after retries | stays INITIATED, `next_check_at` set | stay reserved | 202 PROCESSING | later, via repair |

### 8.4 Repair worker (in-doubt transactions)

It runs every 1 s in every `transaction-service` instance:

```sql
SELECT … FROM send_money_txn
 WHERE status = 'INITIATED' AND next_check_at <= now()
   AND created_at < now() - interval '3 seconds'
 ORDER BY next_check_at LIMIT 200
 FOR UPDATE SKIP LOCKED;
```

**Claim, then process.** The `SELECT … FOR UPDATE SKIP LOCKED` runs in a short transaction that also pushes `next_check_at` forward by 30 s (a lease) and commits at once. The ledger calls happen **outside** any database transaction, so no row lock is ever held across a network call.

For each row it **re-sends the identical posting** (the safest probe, since it is idempotent) and finalises exactly as in 8.3. It **never** marks a row FAILED without a 422 from the ledger. Backoff goes into `next_check_at` (1 s, 2 s, 4 s … capped at 60 s), with `ledger_attempts` incremented; an alert fires after 10 attempts. All finalising updates are compare-and-set (`WHERE status = 'INITIATED'`), so a request thread and the repair worker can never both finalise one row.

**Rows with no posting at all** (a crash between DB transaction #1 and the ledger call) are handled by the same rule: the repair worker's re-send *is* the first posting. If the sender's balance has since dropped, the ledger rejects it and the row becomes FAILED. Either way, the ledger decides.

### 8.5 Failure matrix

| # | Failure | Result |
| --- | --- | --- |
| F1 | Insufficient balance | 422; row FAILED; limits released; nothing posted |
| F2 | Ledger response lost after commit | Retry gets `exists` → 200 COMPLETED |
| F3 | `ledger-service` instance dies mid-call | Retry hits another instance → posts or gets `exists` |
| F4 | TigerBeetle slow or unreachable | `ledger-service` fences → 503; after retries, 202 PROCESSING; repair resolves once it's back |
| F5 | `transaction-service` dies after DB transaction #1 | Row INITIATED → repair posts or rejects |
| F6 | `transaction-service` dies after the ledger commit, before finalising | Repair gets `exists` → COMPLETED + event |
| F7 | PostgreSQL down at step 6 | 503 before any money moves |
| F8 | PostgreSQL down at finalisation | Money moved (ledger truth); row stays INITIATED; client gets 202; repair finalises when PostgreSQL returns |
| F9 | RabbitMQ down | Money unaffected; `event_published_at` stays null; republisher sends later (section 9) |
| F10 | Duplicate client request | Unique `(sender, client_ref)` → stored outcome or 202 if still in flight |

**Last line of defence: the ledger wins.** Reconciliation (FR-08) compares rows with postings. If it ever finds a FAILED row whose legs **are** posted (possible only after a pathological pause longer than every deadline above), it MUST flip the row to COMPLETED, re-count the limits, publish a corrective `SendMoneyCompleted` and raise a high-severity alert. Money is never adjusted to match the record.

## 9. Event publishing to RabbitMQ (no outbox)

As required, there is **no outbox table**: `transaction-service` publishes directly to RabbitMQ after the final status is committed. The only cost of skipping the outbox is the gap between "row committed" and "broker confirmed". It's closed by the `event_published_at` column on the transaction row plus a republisher. That adds no table and no write on the request path.

**Topology** (declared by `transaction-service` at start-up, idempotent)

| Object | Name | Settings |
| --- | --- | --- |
| Exchange | `mfs.transactions` | topic, durable |
| Routing keys | `send-money.completed`, `send-money.failed` | — |
| Dead-letter exchange | `mfs.transactions.dlx` | topic, durable |
| Demo queue (for tests only) | `audit.send-money` | **quorum** queue, bound to `send-money.#`, DLX set |

Real consumers (notification, history, AML…) are out of scope. Each will own its own quorum queue bound to the exchange.

**Message**

| Property | Value |
| --- | --- |
| `message_id` | Deterministic: UUIDv5 of `txnId + eventType`, so a republish carries the same ID and consumers can dedupe |
| `delivery_mode` | 2 (persistent) |
| `content_type` | `application/json` |
| headers | `event-type`, `schema-version: 1`, `txn-id`, `occurred-at`, `traceparent` |

```json
{"eventId":"…","eventType":"SendMoneyCompleted","schemaVersion":1,"occurredAt":"2026-10-07T09:14:03.211Z",
 "txnId":"0192f5a4-…","senderWalletId":42,"receiverWalletId":77,"amount":100000,"fee":500,
 "vat":65,"commission":87,"feeIncome":348,"currency":"BDT","ledgerTimestamp":1791350858928000000,
 "failureCode":null}
```

**Publishing rules**

1. **Publish only after the final status is committed** in PostgreSQL. Never before, and never inside the database transaction.
2. **Off the request path:** hand the event to an `EventPublisher` that sends it on a virtual thread after the HTTP response is written. The response never waits for the broker.
3. **Publisher confirms + mandatory:** `publisher-confirm-type=correlated`, `publisher-returns=true`, `template.mandatory=true`. Each send carries `CorrelationData(txnId)`.
4. **On ack:** add `txnId` to an in-memory buffer. A flusher writes them every 100 ms (or every 500 IDs) in one statement: `UPDATE send_money_txn SET event_published_at = now() WHERE txn_id = ANY(?)`, with `SET LOCAL synchronous_commit = off`, because losing this mark only causes a harmless republish.
5. **On nack, return or timeout (5 s):** log, count, and leave the mark null. The republisher handles it.
6. **Republisher:** every 5 s, claim up to 500 rows `WHERE status <> 'INITIATED' AND event_published_at IS NULL AND completed_at < now() - interval '10 seconds'` (partial index `smt_unpublished`, `FOR UPDATE SKIP LOCKED`, lease pattern as in 8.4) and publish them again.
7. **Delivery is at-least-once.** Consumers MUST dedupe by `message_id`. Ordering across transactions is not guaranteed; consumers use `occurredAt` and `ledgerTimestamp`.

**Throughput settings:** one `CachingConnectionFactory` per instance with a channel cache sized for concurrency (for example 64), confirms processed asynchronously (never `waitForConfirms` on the request path), and messages kept small (about 400 bytes). RabbitMQ quorum queues comfortably absorb the target rate on one node; production runs 3 nodes.

## 10. Code structure, SOLID and design patterns

**Repository (one Gradle build, Kotlin DSL, shared version catalog)**

```text
send-money-poc/
  settings.gradle.kts            include("transaction-service", "ledger-service")
  gradle/libs.versions.toml      Spring Boot 4.1.1, tigerbeetle-java 0.17.x, Testcontainers, …
  build-logic/                   convention plugin: Java 25 toolchain, Error Prone, Checkstyle, JaCoCo
  transaction-service/
  ledger-service/
  contracts/                     transaction-api.yaml, ledger-api.yaml, events/send-money-v1.json (JSON Schema)
  deploy/                        docker-compose.yml, haproxy.cfg, rabbitmq definitions, pg init
  loadtest/                      k6: seed.js, send-money.js, hot-sender.js, hot-receiver.js
  docs/decisions.md
```

The services share **no code module**. Contracts are shared as OpenAPI and JSON Schema files, so each service deploys independently.

**`transaction-service` packages (hexagonal)**

```text
com.poc.txn
  api/            SendMoneyController, QuoteController, WalletController, request/response records, ProblemDetail advice
  application/    SendMoneyService (use case), QuoteService, RepairWorker, EventRepublisher, ReconciliationService
  domain/         SendMoney (aggregate), TxnStatus (state machine), Pricing, Money, Wallet, LimitPolicy,
                  rules/SendMoneyRule (+ SelfTransferRule, WalletActiveRule, ReceiverTypeRule, AmountRangeRule),
                  fee/FeeCalculator (+ SlabFeeCalculator), LegPlanner
  port/out/       LedgerPort, TxnRepository, WalletRepository, LimitRepository, RuleRepository, EventPublisherPort, TxnIdGenerator
  adapter/out/    ledger/HttpLedgerClient, jdbc/Jdbc*Repository, amqp/RabbitEventPublisher, cache/Caffeine*Cache
  config/         @ConfigurationProperties records, beans, @EnableResilientMethods
```

**`ledger-service` packages (hexagonal)**

```text
com.poc.ledger
  api/            PostingController, AccountController, records, ProblemDetail advice
  application/    PostingService, AccountService, ChartOfAccountsBootstrap
  domain/         Posting, Leg, PostingOutcome (sealed: Posted | Rejected | Unknown), RejectionCode, TransferIds
  port/out/       LedgerStore
  adapter/out/    tigerbeetle/TigerBeetleLedgerStore, tigerbeetle/FencedClientHolder, tigerbeetle/ResultMapper
  config/         TigerBeetleProperties, client bean lifecycle
```

**Key abstractions**

```java
public sealed interface PostingOutcome {
  record Posted(long ledgerTimestamp, boolean replay) implements PostingOutcome {}
  record Rejected(RejectionCode code, int legIndex)     implements PostingOutcome {}
  record Unknown(String reason)                          implements PostingOutcome {}
}
public interface LedgerPort { PostingOutcome post(PostingRequest request); }   // transaction-service
public interface LedgerStore { PostingOutcome createLinked(Posting posting); }  // ledger-service
public interface SendMoneyRule { Optional<FailureCode> check(SendMoneyContext ctx); }
public interface FeeCalculator { Pricing price(Product p, int tier, long amount); }
```

**Design patterns**

| Pattern | Where | Why |
| --- | --- | --- |
| Ports & Adapters (hexagonal) | Both services | Domain is free of Spring, JDBC, HTTP and TigerBeetle types; adapters can be swapped and tested in isolation |
| Chain of Responsibility | `List<SendMoneyRule>`, ordered | New rule = new class; no edits to existing ones |
| Strategy | `FeeCalculator` (slab, percent), `LimitPolicy` | Pricing and limits change by configuration |
| State machine | `TxnStatus` with allowed transitions; SQL compare-and-set | Illegal transitions impossible; races closed |
| Adapter | `HttpLedgerClient`, `TigerBeetleLedgerStore`, `RabbitEventPublisher` | Isolate vendor APIs |
| Repository | `Jdbc*Repository` | Explicit SQL behind ports |
| Factory | `TxnIdGenerator`, `TransferIds` | One owner each for ID formats |
| Idempotent Receiver | Both APIs; ledger via deterministic IDs | Safe retries everywhere |
| Retry with backoff + jitter | `RetryTemplate` around `LedgerPort` | Ride out transient faults |
| Bulkhead | `@ConcurrencyLimit` on `LedgerPort` and on the posting endpoint | Shed load instead of queueing (NFR-07) |
| Fencing token (holder swap) | `FencedClientHolder` | Makes timed-out postings definitive (8.2) |
| Sealed result types | `PostingOutcome`, `QuoteResult` | The compiler enforces exhaustive outcome handling (`switch` with patterns) |

**SOLID, applied**

- **S**: `SendMoneyService` orchestrates only; rules decide validity; `FeeCalculator` prices; `LegPlanner` builds legs; adapters translate.
- **O**: new rules, fee strategies or products are new classes. The ledger service is product-agnostic.
- **L**: any `LedgerPort` (HTTP, in-memory fake for tests) honours the same idempotency contract.
- **I**: small ports (`LedgerPort` has one method, `TxnRepository` only what the use case needs).
- **D**: application code depends on ports; Spring wires adapters by constructor injection. No field injection, no static singletons.

**Coding standards:** records for DTOs and value objects; `final` by default; no Lombok; exhaustive `switch` over sealed types; no checked exceptions leaking out of adapters; one SQL statement per repository method, written as text blocks; structured JSON logging with `txnId` and `traceId` in the MDC.

## 11. Performance and scalability instructions (mandatory)

**Budget per Send Money on the request path:** 3 SQL statements in 2 short transactions (insert + limit update, then finalise), 1 HTTP call to the ledger, 1 TigerBeetle request (auto-batched with others). Nothing else may block the response.

| # | Rule | Applies to | Why |
| --- | --- | --- | --- |
| P1 | `spring.threads.virtual.enabled=true`; no custom request thread pools | both | Thousands of concurrent blocking calls with no pool tuning |
| P2 | **One shared TigerBeetle `Client` per JVM**, behind `FencedClientHolder`; never one per request | ledger | The client is thread-safe and **auto-batches concurrent requests**; this is the main throughput lever |
| P3 | Use `createTransfersAsync(…).get(deadline)` / `lookupTransfersAsync`, not the blocking methods | ledger | Parks virtual threads instead of pinning carrier threads inside JNI |
| P4 | One posting = one small `TransferBatch`; **don't** add an application-level batcher | ledger | The client already merges concurrent batches; a second batcher only adds latency |
| P5 | **No JPA/Hibernate.** Use `JdbcClient` with explicit SQL; one statement per repository method | transaction | No ORM overhead, exact control over round trips and locks |
| P6 | HikariCP pool ≈ 2× the database's CPU cores (start at 20 per instance), `connectionTimeout` 250 ms; JDBC URL `reWriteBatchedInserts=true` | transaction | Small pools beat big ones on PostgreSQL; virtual threads queue cheaply for a connection |
| P7 | Keep transactions short: **never** hold a DB transaction or row lock across the ledger call | transaction | Lock time = network time otherwise |
| P8 | Caffeine caches: wallet by MSISDN (TTL 10 s, max 500 k), `fee_rule` and `limit_rule` (refresh 60 s); system account IDs from configuration | transaction | Removes 3–4 reads per request |
| P9 | One `RestClient` bean on `JdkClientHttpRequestFactory` with a shared `HttpClient` (HTTP/1.1, keep-alive), explicit connect and read timeouts | transaction | Connection reuse; no handshake per call |
| P10 | `@ConcurrencyLimit` on the ledger port (start at 512 per instance) and on the posting endpoint; when saturated return 503 + `Retry-After: 1` immediately | both | Backpressure instead of unbounded queues (NFR-07) |
| P11 | Event publish and confirm handling **after** the response, on virtual threads; batched `event_published_at` updates with `synchronous_commit = off` | transaction | Keeps the broker off the latency path |
| P12 | `txnId` generation lock-free: `System.currentTimeMillis()` + `ThreadLocalRandom`; no DB sequence, no UUIDv4 for primary keys | transaction | Sequential B-tree inserts, no contention |
| P13 | Tables with frequent updates (`send_money_txn`, `wallet_limit_usage`) use a fillfactor (80 / 70) for HOT updates; autovacuum tuned more aggressively on them (scale factor 0.02) | PostgreSQL | Avoid bloat and index churn under sustained updates |
| P14 | Only the indexes in section 6; partial indexes for in-flight rows | PostgreSQL | Every extra index is extra write cost |
| P15 | JSON via Spring Boot's default Jackson; DTOs as records; never log request or response bodies; JSON logs through an async appender | both | Less allocation and I/O per request |
| P16 | JVM: `-XX:+UseZGC` (generational), `-Xms` = `-Xmx`, `-XX:+AlwaysPreTouch`, container-aware memory; optionally a JDK AOT cache for faster start-up | both | Sub-millisecond GC pauses, stable latency |
| P17 | Both services are **stateless**; scale by adding instances behind HAProxy. No sticky sessions, no in-memory state that must survive a restart. | both | Horizontal scaling (NFR-01) |
| P18 | Profile before optimising further: JFR recordings during each k6 run, stored with the results | both | Evidence-driven tuning |

**Scaling model**

- **`transaction-service`:** CPU-bound on JSON, rules and TLS. Add instances.
- **`ledger-service`:** thin, mostly waiting on TigerBeetle. 2 instances give availability; add more only if CPU-bound.
- **PostgreSQL:** about 4 statements per Send Money (3 on the request path + the batched publish mark), so 3,000 TPS is about 12,000 short statements/s. That's within one well-provisioned primary on NVMe. Beyond that: monthly partitioning of `send_money_txn` (moving idempotency to a separate unpartitioned key table), then sharding by sender.
- **TigerBeetle:** far above the POC target; no scaling action needed. Production uses 6 replicas.
- **RabbitMQ:** about 1 small persistent message per Send Money; one node for the POC, a 3-node quorum cluster in production.
- **Hot rows:** the only PostgreSQL row lock contended per transaction is the sender's `wallet_limit_usage` row (per sender, not global). System accounts live in TigerBeetle, where hot accounts cost nothing.

## 12. Configuration, deployment and observability

**`transaction-service` — `application.properties`**

```properties
spring.application.name=transaction-service
server.port=8080
spring.threads.virtual.enabled=true
server.shutdown=graceful

spring.datasource.url=jdbc:postgresql://postgres:5432/txn_db?reWriteBatchedInserts=true
spring.datasource.username=txn
spring.datasource.password=${TXN_DB_PASSWORD}
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=250
spring.flyway.enabled=true

spring.rabbitmq.host=rabbitmq
spring.rabbitmq.username=${RABBIT_USER}
spring.rabbitmq.password=${RABBIT_PASSWORD}
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.template.mandatory=true
spring.rabbitmq.cache.channel.size=64

poc.ledger.base-url=http://haproxy:8090
poc.ledger.connect-timeout=100ms
poc.ledger.read-timeout=1200ms
poc.ledger.total-budget=3s
poc.ledger.max-retries=2
poc.ledger.concurrency-limit=512
poc.ledger.accounts.fee-income=00000000-0000-0000-0000-0000000000c8
poc.ledger.accounts.vat-payable=00000000-0000-0000-0000-0000000000d2
poc.ledger.accounts.commission-payable=00000000-0000-0000-0000-0000000000dc
poc.ledger.accounts.issuance=00000000-0000-0000-0000-000000000384

poc.business.zone=Asia/Dhaka
poc.quote.ttl=5m
poc.quote.signing-key=${QUOTE_SIGNING_KEY}
poc.cache.wallet.ttl=10s
poc.cache.wallet.max-size=500000
poc.cache.rules.refresh=60s

poc.repair.interval=1s
poc.repair.batch-size=200
poc.repair.lease=30s
poc.repair.max-backoff=60s
poc.repair.alert-after-attempts=10
poc.events.exchange=mfs.transactions
poc.events.confirm-timeout=5s
poc.events.flush-interval=100ms
poc.events.republish.interval=5s
poc.events.republish.batch-size=500
poc.security.api-key=${API_KEY}

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=0.1
```

**`ledger-service` — `application.properties`**

```properties
spring.application.name=ledger-service
server.port=8081
spring.threads.virtual.enabled=true
server.shutdown=graceful

poc.tigerbeetle.cluster-id=0
poc.tigerbeetle.addresses=tigerbeetle:3000
poc.tigerbeetle.request-deadline=800ms
poc.tigerbeetle.ledger=1
poc.posting.max-legs=8
poc.posting.concurrency-limit=1024
poc.accounts.bootstrap=true

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.tracing.sampling.probability=0.1
```

**Deployment (Docker Compose, Linux host)**

| Container | Image / build | Notes |
| --- | --- | --- |
| `tigerbeetle-format` | `ghcr.io/tigerbeetle/tigerbeetle:0.17.x` | `profiles: [init]`; one-time `format --cluster=0 --replica=0 --replica-count=1`; `security_opt: [seccomp=unconfined]` (io\_uring) |
| `tigerbeetle` | same | `start --addresses=0.0.0.0:3000`; same `security_opt`; named volume |
| `postgres` | `postgres:18` | `txn_db`; `shared_buffers` 25% of RAM, `max_connections` sized for pools, `wal_compression=on` |
| `rabbitmq` | `rabbitmq:4-management` | Definitions file for exchange, DLX and demo quorum queue |
| `ledger-service` | build | `replicas: 2` |
| `transaction-service` | build | `replicas: 2` |
| `haproxy` | `haproxy:3.x` | `:8080` → transaction instances; `:8090` → ledger instances; health-checked round robin with keep-alive |
| `prometheus`, `grafana`, `jaeger` | official images | Metrics, dashboards, traces |
| `toxiproxy` | `ghcr.io/shopify/toxiproxy` | Test profile only: between transaction → ledger and ledger → TigerBeetle |

**Observability**

- **Traces:** OpenTelemetry through Micrometer Tracing. W3C `traceparent` crosses transaction → ledger over HTTP and into RabbitMQ message headers.
- **Metrics (Prometheus):** `sendmoney_requests_total{outcome,code}`, `sendmoney_duration_seconds` (histogram), `ledger_posting_duration_seconds{outcome}`, `ledger_tb_request_seconds`, `ledger_client_fence_total`, `txn_in_doubt` (gauge), `txn_repair_attempts_total`, `events_publish_total{result}`, `events_unpublished` (gauge), `limit_rejections_total`, plus HikariCP, JVM, RabbitMQ and HTTP client metrics.
- **Alerts:** in-doubt count > 0 for 2 minutes; any `ledger_client_fence_total` increase; unpublished events older than 1 minute; any reconciliation mismatch; p99 above target for 5 minutes.
- **Logs:** JSON, one line per request outcome, with `txnId`, `traceId`, `outcome`, `code` and `durationMs`. MSISDNs masked to their last 3 digits.

## 13. Testing, acceptance and delivery plan

**Test levels**

| Level | Tooling | MUST cover |
| --- | --- | --- |
| Unit | JUnit 5, AssertJ, jqwik (property-based) | Fee/VAT/commission split always sums to the fee (property test over the full amount range); every rule; `TxnStatus` transitions; ID derivation; TigerBeetle result mapping |
| Integration | Testcontainers: PostgreSQL 18, RabbitMQ 4, TigerBeetle as a `GenericContainer` with `seccomp=unconfined` | Every SQL statement (limit reservation and release at day/month boundaries, CAS finalise); the real linked posting (success, insufficient funds, replay → `exists`); publisher confirms and returns |
| Contract | OpenAPI validators on both sides; JSON Schema for events | Transaction ↔ ledger stay in sync; events match `send-money-v1.json` |
| Fault | Toxiproxy (latency, timeouts, reset), `docker kill -9`, `docker pause` | Every row F1–F10 of section 8.5, asserting the invariants afterwards |
| Load | k6 | `send-money.js`: 100,000 pre-funded wallets, random pairs, ramp to target, 15 minutes. `hot-sender.js`: one sender at its limits. `hot-receiver.js`: 10,000 senders to one merchant-like receiver. |

**Invariants checked after every integration, fault and load run** (via the reconciliation endpoint and a ledger balance scan):

1. Issuance debit balance = sum of all customer + fee income + VAT payable + commission payable balances.
2. Every COMPLETED row has all its legs posted, with matching amounts; every FAILED row has none.
3. No INITIATED row older than 60 s.
4. `wallet_limit_usage` day and month totals = sum of the day's and month's COMPLETED amounts per sender.
5. Every final row has `event_published_at` set within 60 s, and the demo queue holds exactly one distinct `message_id` per final row.

**Delivery milestones**

1. **M1 – Skeleton:** Gradle build, convention plugin, both services boot, Flyway schema, Compose stack up, health checks green, CI pipeline.
2. **M2 – Ledger service:** chart-of-accounts bootstrap, account and posting endpoints, result mapping, fencing, integration tests against real TigerBeetle.
3. **M3 – Send Money happy path:** rules, pricing, limits, reserve → post → finalise, quote, status; contract tests.
4. **M4 – Failure handling:** retry policy, deadline hierarchy, repair worker, reconciliation; fault suite F1–F10 green.
5. **M5 – Events:** RabbitMQ topology, async publish with confirms, published-mark flusher, republisher; event tests green.
6. **M6 – Performance:** apply section 11, run the k6 scenarios with JFR, tune, and record results in `docs/performance.md` against section 1, criterion 5.

**Acceptance:** all milestones done; all five invariants hold after the full fault and load suites; the performance criterion is met or the gap is explained with profiling evidence; `docs/decisions.md` lists every choice made where this spec was silent.

## Sources

- TigerBeetle: [Java client](https://docs.tigerbeetle.com/coding/clients/java/), [Client.java (async API, thread safety)](https://github.com/tigerbeetle/tigerbeetle/blob/main/src/clients/java/src/main/java/com/tigerbeetle/Client.java), [Requests and batching](https://docs.tigerbeetle.com/coding/requests/), [Transfer reference](https://docs.tigerbeetle.com/reference/transfer/), [Account reference](https://docs.tigerbeetle.com/reference/account/), [Docker](https://docs.tigerbeetle.com/operating/deploying/docker/)
- Spring: [Spring Boot 4.1.1](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/), [Spring Framework resilience features (@Retryable, @ConcurrencyLimit, RetryTemplate)](https://docs.spring.io/spring-framework/reference/core/resilience.html)
