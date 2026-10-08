# PROGRESS — transaction-service

Work loop: pick the next unchecked item → implement → `./gradlew clean build` → fix until green → tick.

## Definition of DONE

- [x] Every feature in section 2 (below) is implemented as specified.
- [x] `./gradlew clean build` succeeds with zero compile errors and all unit/slice tests passing.
- [x] End-to-end test (Testcontainers PostgreSQL + RabbitMQ, WireMock ledger): COMPLETED with event published; INSUFFICIENT_FUNDS → FAILED with the limit released; idempotent replay (same key + body → same result; same key + different body → 409); ledger timeout → 202, then repair worker → COMPLETED.
- [x] `openapi/transaction-api.yaml` is valid OpenAPI 3.1 and matches the implemented endpoints; the event JSON Schema matches the published payload.
- [x] `docker compose config` is valid; Dockerfile builds. (The `full` profile was removed on request: ledger-service runs as a separate service, decision L7.)
- [x] README explains build, running against the separate ledger-service, behaviour while it is unavailable, and the API key.

## Features (section 2)

- [x] 0. Skeleton: `build.gradle.kts`, package layout, domain types, ports, API records, config properties — compiles
- [x] 1. Public API per spec 7.1 + test-profile support endpoints, API-key filter, RFC 9457 Problem Details with `code`
- [x] 2. Flyway migrations: exact schema of spec 6.1 + seed data (spec 3)
- [x] 3. Business rules BR-01..BR-09 (rule chain, slab fee, VAT half-up, commission floor, fee income remainder)
- [x] 4. Send Money algorithm per spec 5 (reserve tx → one ledger call → CAS / CTE finalise → 200 / 422 / 202)
- [x] 5. txnId generator (spec 6.3) + legs (spec 6.2, zero legs omitted) + system account IDs from config
- [x] 6. Ledger client (spec 8.3): deadlines, RetryTemplate on 503/IO/timeout only, identical resend, `@ConcurrencyLimit` → 503 + Retry-After
- [x] 7. Repair worker (spec 8.4): claim-then-process lease, resend outside DB tx, never FAILED without 422, backoff, CAS
- [x] 8. Events (spec 9): topology, async publish, confirms + returns, UUIDv5 message_id, headers, batched publish mark, republisher
- [x] 9. Reconciliation endpoint (FR-08, "ledger wins")
- [x] 10. Caches (P8) and performance rules P1, P5–P15
- [x] 11. OpenAPI 3.1 contract + event JSON Schema; served at `GET /openapi.yaml` (+ Swagger UI if compatible)
- [x] 12. Dockerfile + docker-compose (postgres, rabbitmq; ledger-service external) + README

## Notes

- Docker is available on this host (the sandbox blocks the socket, so builds that run Testcontainers run unsandboxed).
- Done so far (each with green tests): A domain (102 tests), B persistence on PostgreSQL 18 (48), C ledger client on WireMock (30),
  D AMQP on RabbitMQ 4 (36), E contracts/compose (validated). E2E: 5/5 scenarios green on real containers.
- Ledger client aligned with `../ledger-service/openapi/ledger-api.yaml` (authoritative; appeared mid-build): 37 tests.
- Contract tests: OpenAPI 3.1 valid + endpoint parity, event payload vs JSON Schema, 22 API slice cases — green.
- `docker build` OK; `docker compose config` OK (default + full). Standalone stack smoke-tested end to end
  (17/17 checks: register, fund, quote, send, replay, 409, status, balance, 422, reconciliation, 401, /openapi.yaml,
  Swagger UI, Prometheus business metrics).
- Application-layer unit tests with in-memory fakes: 204 (incl. reconciliation edge cases).
- **Final `./gradlew clean build`: BUILD SUCCESSFUL — 461 tests in 62 suites, 0 failures, 0 skipped.** Docker was
  available, so the E2E and all Testcontainers suites (PostgreSQL 18, RabbitMQ 4) really ran; none was skipped.
