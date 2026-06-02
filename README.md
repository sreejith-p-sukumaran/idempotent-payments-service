# Idempotent Payments Service

A small Spring Boot (Kotlin) service with one meaningful endpoint:

```
POST /payments
Idempotency-Key: <client-generated-key>
```

**The guarantee: retrying the same request never creates a duplicate payment.**
This is how Stripe's API behaves — a recognizable, real-world pattern.

The reasoning behind every decision lives in **[DESIGN.md](./DESIGN.md)**, written
before the code. This README is the practical entry point: what it does, how to
run it, and how it's tested.

## Why it exists

Networks are unreliable. A client sends `POST /payments`, the payment succeeds,
but the response is lost on the way back. The client can't tell whether it
worked, so it **retries**. Without protection, the retry charges the customer
twice. The client attaches a unique **idempotency key** per logical operation,
and the server promises to perform that operation **at most once** — every later
request with the same key replays the original result.

## How it works (in one diagram)

```
POST /payments  +  Idempotency-Key
        │
        ├─ header missing/blank ─────────────► 400
        ├─ body invalid ─────────────────────► 400
        ▼
  INSERT idempotency_record(key, IN_PROGRESS, bodyHash)   [txn 1, commit]
        │
        ├─ INSERT ok (I'm first) ─► do payment work ─► UPDATE COMPLETED [txn 2] ─► 201
        │
        └─ INSERT conflict (key exists) ─► read row
                 ├─ different bodyHash ──────► 422
                 ├─ COMPLETED ───────────────► replay stored response (201, Idempotent-Replayed: true)
                 └─ IN_PROGRESS ─────────────► 409 (Retry-After)
```

The key as a primary key **is** the lock — only one concurrent `INSERT` wins.
The `IN_PROGRESS` row is committed in **its own transaction before** the work
runs, so a concurrent duplicate sees it and fails fast. See DESIGN.md §1–§2 for
why this is the crux.

## API

### `POST /payments`

| | |
|---|---|
| Header | `Idempotency-Key: <string>` (required, non-blank) |
| Body | `{ "amount": <positive integer, minor units>, "currency": "<3-letter ISO>" }` |

Amounts are in the currency's **minor unit** (e.g. `2500` = €25.00).

#### Responses

| Status | When | Notes |
|--------|------|-------|
| `201 Created` | First request for the key | Returns the created payment |
| `201 Created` | Retry of a completed key | Stored response replayed verbatim; adds `Idempotent-Replayed: true` |
| `409 Conflict` | A request with this key is still in flight | RFC 7807 body, `code: IN_PROGRESS`, `Retry-After: 1` |
| `422 Unprocessable Entity` | Key reused with a **different** body | `code: IDEMPOTENCY_KEY_MISMATCH` |
| `400 Bad Request` | Missing/blank key, or invalid body | `code: MISSING_IDEMPOTENCY_KEY` / `VALIDATION_FAILED` |

Errors use [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `ProblemDetail`
(`application/problem+json`) with a stable machine-readable `code`.

#### Example

```bash
curl -i -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f3a1c2e-1111-2222-3333-444455556666' \
  -d '{"amount": 2500, "currency": "EUR"}'
```

Repeating the exact call returns the same `201` body with `Idempotent-Replayed: true`.

## Running locally

Requires JDK 21 and a PostgreSQL instance. Start one with Docker:

```bash
docker run --rm -d --name payments-db \
  -e POSTGRES_DB=payments -e POSTGRES_USER=payments -e POSTGRES_PASSWORD=payments \
  -p 5432:5432 postgres:16

./gradlew bootRun
```

Flyway applies the schema on startup. The app listens on `:8080`.

### Configuration

| Property | Env var | Default | Meaning |
|----------|---------|---------|---------|
| `spring.datasource.url` | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/payments` | DB connection |
| `spring.datasource.username` | `DATABASE_USER` | `payments` | DB user |
| `spring.datasource.password` | `DATABASE_PASSWORD` | `payments` | DB password |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |
| `idempotency.retention` | `IDEMPOTENCY_RETENTION` | `24h` | How long records are kept for replay before a key is treated as fresh |
| `idempotency.cleanup.interval` | `IDEMPOTENCY_CLEANUP_INTERVAL` | `1h` | How often the expiry sweep runs |

A scheduled sweep deletes records past `retention`, which also cleans up rows
stranded in `IN_PROGRESS` by a crash between claim and complete.

## Testing

```bash
./gradlew test
```

Tests need Docker (Testcontainers spins up a real Postgres). Layers:

- **Unit** — `IdempotencyServiceTest` proves the work is **not** redone on a key
  collision, and the replay / in-flight / mismatch branches.
- **Repository slices** (`@DataJpaTest`) — JPA mapping ↔ Flyway schema, the
  unique-constraint lock, and the expiry query, against real Postgres.
- **Web slice** (`@WebMvcTest`) — validation, headers, and outcome→HTTP mapping.
- **Integration** (`@SpringBootTest`) — end-to-end create, byte-identical replay,
  in-flight 409, and `422` on body mismatch.
- **Concurrency** — `PaymentConcurrencyIntegrationTest` fires 20 simultaneous
  requests with the same key and asserts **exactly one** payment is created and
  every response is a (replayed) success or a `409`. This is the test that
  proves the design under the race it exists for.

## Stack

Kotlin · Spring Boot 3.5 · Spring Web · Spring Data JPA · PostgreSQL · Flyway ·
JUnit 5 · MockK · Testcontainers · Java 21.

## Project layout

```
controller/   HTTP boundary: PaymentController, ProblemDetail error handling
service/      IdempotencyService (orchestration), IdempotencyTransactions
              (the two committed phases), PaymentApplicationService, cleanup task
domain/       Payment, IdempotencyRecord (Persistable), value objects
repository/   Spring Data JPA repositories
config/       Clock, idempotency properties + scheduled cleanup wiring
resources/db/migration/   Flyway scripts (V1 payment, V2 idempotency_record)
```
