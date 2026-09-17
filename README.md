# Wallet Service

A REST API for a mini electronic wallet: deposit, withdraw, and transfer money between accounts — built to be **concurrency-safe** and easy to run anywhere with **one Docker command**.

- **JWT authentication** — login to get a token, use it for every wallet call
- **Concurrency-safe transfers** — pessimistic row locks + fixed lock ordering (no deadlocks) + optimistic `@Version`
- **Idempotent transfers** — optional `idempotencyKey` makes a transfer safe to retry (second attempt → `409`)
- **Full traceability** — successful operations are recorded as `SUCCESS`, operations rejected by business rules as `FAILED`
- **Async audit + notification** — fired only after commit, on a bounded dedicated thread pool
- **Health endpoint** — `GET /actuator/health` (public)
- **Login rate limiting** — token bucket on `POST /auth/login` (configurable)
- **OpenAPI 3 + Swagger UI** — every endpoint documented, interactive "Try it out"
- **Flyway migrations** — schema and demo data applied automatically on startup
- **PostgreSQL 16** — reliable persistence
- **Docker Compose** — app + database in one command

---

## Tech stack

| Layer      | Technology                                        |
| ---------- | ------------------------------------------------- |
| Language   | Java 21                                           |
| Framework  | Spring Boot 4.1.1 (Web MVC, Data JPA, Security)   |
| Database   | PostgreSQL 16 (Flyway for migrations)             |
| Auth       | JWT (JJWT 0.12.6, HS256), Spring Security         |
| API docs   | springdoc-openapi 3.1.1 (Swagger UI)              |
| Build      | Gradle 9.7.1                                      |
| Tests      | JUnit 5 (includes concurrency tests)              |
| Deployment | Docker + Docker Compose                           |

---

## Quick start (Docker — one command)

```bash
docker compose up --build -d
```

That's it. The command builds the app image, starts PostgreSQL, waits until it's healthy, runs the Flyway migrations, seeds demo data, and starts the API.

| What              | Where                                       |
| ----------------- | ------------------------------------------- |
| API               | http://localhost:8080                       |
| Swagger UI        | http://localhost:8080/swagger-ui.html       |
| OpenAPI JSON spec | http://localhost:8080/v3/api-docs           |
| PostgreSQL        | localhost:5432 (db `wallet`, user `wallet`, password `wallet`) |

Useful commands:

```bash
docker compose logs -f app     # follow app logs
docker compose down            # stop everything (keeps DB data)
docker compose down -v         # stop and wipe the database
```

## Quick start (local dev, no Docker for the app)

Start only the database with Compose, then run the app with Gradle:

```bash
docker compose up -d db
./gradlew bootRun
```

---

## Demo credentials

Users are hardcoded in `CustomUserDetailsService` (a users table is out of scope for this exercise):

| Username | Password | Role        |
| -------- | -------- | ----------- |
| alice    | password | ROLE_USER   |
| admin    | admin    | ROLE_ADMIN  |

## Seeded accounts

`V2__seed_demo_data.sql` creates three accounts so the API is usable immediately:

| ID | Balance  | Status  | Purpose                       |
| -- | -------- | ------- | ----------------------------- |
| 1  | 1000.00  | ACTIVE  | deposit / withdraw / transfer |
| 2  | 500.00   | ACTIVE  | transfer destination          |
| 3  | 250.00   | BLOCKED | demonstrates the 403 response |

---

## API reference

All endpoints except `/auth/login` require the header:

```
Authorization: Bearer <token>
```

### Error responses

Every error uses the same JSON shape:

```json
{
  "timestamp": "2026-09-17T14:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found: 99"
}
```

Validation errors (400) additionally include a `details` object with field names.

| Status | Meaning                                        |
| ------ | ---------------------------------------------- |
| 200    | Success                                        |
| 400    | Validation failed, insufficient balance, invalid operation |
| 401    | Missing/invalid/expired token, bad credentials |
| 403    | Account blocked or inactive                    |
| 404    | Account not found, unknown route               |
| 409    | Duplicate transaction (idempotency key already used) |
| 429    | Too many login attempts (rate limit)           |
| 500    | Unexpected error                               |

---

### 1. Login — `POST /auth/login`

Public. Verifies credentials and returns a JWT (valid for 1 hour by default).

**Request**

```json
{ "username": "alice", "password": "password" }
```

**Response `200`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "alice",
  "role": "ROLE_USER"
}
```

`401` — invalid username or password.

**curl**

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}'
```

---

### 2. Get account — `GET /accounts/{id}`

Returns the current balance and status.

**Response `200`**

```json
{ "id": 1, "balance": 1400.00, "status": "ACTIVE" }
```

`401`, `403` (blocked), `404`.

**curl**

```bash
curl http://localhost:8080/accounts/1 -H "Authorization: Bearer $TOKEN"
```

---

### 3. Deposit — `POST /accounts/{id}/deposit`

Adds money to an account. Amount must be greater than zero.

**Request**

```json
{ "amount": 100 }
```

**Response `200`** — updated `AccountResponse` with the new balance.

`400` (amount missing or ≤ 0), `401`, `403` (blocked), `404`.

**curl**

```bash
curl -X POST http://localhost:8080/accounts/1/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":100}'
```

---

### 4. Withdraw — `POST /accounts/{id}/withdraw`

Removes money from an account. Fails with `400` if the balance is insufficient.

**Request**

```json
{ "amount": 50 }
```

**Response `200`** — updated `AccountResponse`.

`400` (amount ≤ 0 **or** insufficient balance), `401`, `403` (blocked), `404`.

**curl**

```bash
curl -X POST http://localhost:8080/accounts/1/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":50}'
```

---

### 5. Transfer — `POST /transfers`

Moves money from one account to another **atomically**. Both balances update or neither does. Fails with `400` if source and destination are the same account.

**Request**

```json
{ "sourceAccountId": 1, "destinationAccountId": 2, "amount": 50 }
```

**Response `200`**

```json
{ "message": "Transfer completed successfully" }
```

`400` (same account, invalid amount, insufficient balance), `401`, `403` (either account blocked), `404`, `409`.

#### Idempotent transfers (retry-safe)

Add an optional `idempotencyKey` to the body. If the same key is sent again, the transfer is **not executed twice**: the server replies `409` and the balances are left untouched.

```json
{ "sourceAccountId": 1, "destinationAccountId": 2, "amount": 50, "idempotencyKey": "client-op-1234" }
```

The key is stored in a unique partial index in the `transactions` table — the database, not the application, is the source of truth, so two concurrent retries are also safe.

**curl**

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":50}'
```

---

### 6. Transaction history — `GET /accounts/{id}/transactions`

Lists all transactions where the account is the source or destination. Operations rejected by a business rule (insufficient balance, blocked account, transfer to self) are recorded with `"status": "FAILED"`, so the history reflects both successful and refused operations.

**Response `200`** — array of transactions:

```json
[
  {
    "id": 12,
    "type": "TRANSFER",
    "amount": 50.00,
    "status": "SUCCESS",
    "sourceAccountId": 1,
    "destinationAccountId": 2,
    "timestamp": "2026-09-17T14:05:00"
  }
]
```

`401`, `404`.

**curl**

```bash
curl http://localhost:8080/accounts/1/transactions -H "Authorization: Bearer $TOKEN"
```

---

### 7. Health — `GET /actuator/health`

Public endpoint (no token required) exposing the service state:

```json
{ "status": "UP" }
```

---

### 8. Login rate limiting

`POST /auth/login` is protected by an in-memory token bucket: by default **10 attempts per minute per client IP**, then `429 Too Many Requests`. Loopback clients are exempt so local tooling is not throttled. Configure or disable it with the `LOGIN_RATE_LIMIT` environment variable (`0` disables it).

---

### Full walkthrough

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

# 2. Check balance
curl http://localhost:8080/accounts/1 -H "Authorization: Bearer $TOKEN"

# 3. Deposit 100
curl -X POST http://localhost:8080/accounts/1/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":100}'

# 4. Transfer 50 from account 1 to account 2
curl -X POST http://localhost:8080/transfers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":50}'

# 5. History
curl http://localhost:8080/accounts/1/transactions -H "Authorization: Bearer $TOKEN"
```

---

## Swagger UI

Open **http://localhost:8080/swagger-ui.html** (redirects to `/swagger-ui/index.html`). The docs pages are public — no login needed.

All six endpoints are grouped by tag: **Authentication**, **Accounts**, **Transfers**, each with a summary, description, parameters, and documented status codes.

**To call protected endpoints from the UI:**

1. First execute `POST /auth/login` with `alice` / `password` and copy the `token` from the response.
2. Click the green **Authorize** button (top right).
3. Paste the token (no `Bearer` prefix needed) and click **Authorize** → **Close**.
4. Expand any endpoint and click **Try it out** → fill the body → **Execute**. The UI sends the token automatically.

The raw OpenAPI 3 spec is available at **http://localhost:8080/v3/api-docs**.

---

## Concurrency design

The transfer endpoint is the interesting part. Two problems are solved:

1. **Lost updates** — every account is locked with `SELECT ... FOR UPDATE` (`AccountRepository.findWithLockById`) inside the transaction, so concurrent operations serialize on the account row.
2. **Deadlocks** — transfers lock both accounts in a **fixed order** (lower ID first). Whether a request goes A→B or B→A, both lock the same rows in the same order, so no cycle can form.

Accounts also carry an optimistic `@Version` column as a second layer of protection.

**Proof** — `TransferServiceConcurrencyTest` runs three scenarios against a real database:
- 10 parallel withdrawals of 100 on an account with 1000 → all 10 succeed, final balance is exactly 0.
- 5 parallel A→B transfers racing 5 parallel B→A transfers → no deadlock, net balance unchanged.
- 5 parallel withdrawals racing 5 parallel transfers out of the same source account → source exactly 0, destination exactly 1500.

```bash
./gradlew test
```

(Needs a reachable PostgreSQL — run `docker compose up -d db` first.)

### Async audit events

After every successful deposit/withdraw/transfer, an `OperationCompletedEvent` is published. It only fires **after the DB transaction commits** (`@TransactionalEventListener(AFTER_COMMIT)`) and runs on a separate thread (`@Async`), so auditing never slows down or blocks the request.

- **No false notifications**: if the main transaction rolls back (insufficient balance, blocked account, DB error), the event is never delivered — an operation that did not happen is never notified.
- **Bounded executor**: `AsyncConfig` defines a dedicated `ThreadPoolTaskExecutor` (`wallet-async-*`, 2–4 threads, queue of 100) instead of Spring's unbounded default.
- **Structured logs**: the audit trace and the simulated notification are emitted as key=value SLF4J logs (`AUDIT operation_type=... amount=...`, `Notification sent to ...`).
- **Failed operations** are traced synchronously in a `REQUIRES_NEW` transaction (see `TransactionRecorder`), so the trace survives the rollback of the failing operation.

---

## Configuration

Everything is configurable via environment variables:

| Variable            | Default                                          | Description                      |
| ------------------- | ------------------------------------------------ | -------------------------------- |
| `DB_URL`            | `jdbc:postgresql://localhost:5432/wallet`        | JDBC URL (Docker Compose sets it to `db:5432`) |
| `DB_USERNAME`       | `wallet`                                         | Database user                    |
| `DB_PASSWORD`       | `wallet`                                         | Database password                |
| `JWT_SECRET`        | `change-me-this-secret-must-be-at-least-32-bytes-long` | HS256 signing key (≥ 32 bytes) |
| `JWT_EXPIRATION_MS` | `3600000`                                        | Token lifetime in ms (1 hour)    |
| `LOGIN_RATE_LIMIT`  | `10`                                             | Max login attempts per minute per client IP (`0` disables) |

---

## Project structure

```
src/main/java/com/wallet/wallet
├── WalletApplication.java        # entry point
├── config/                       # OpenAPI (Swagger) + async config
├── controller/                   # REST controllers (annotated for Swagger)
│   ├── AuthController.java       # POST /auth/login
│   ├── AccountController.java    # GET/POST /accounts/**
│   └── TransferController.java   # POST /transfers
├── dto/                          # request/response records with validation
├── entity/                       # Account, Transaction (+ enums)
├── repository/                   # Spring Data JPA repositories (incl. row-lock query)
├── service/                      # AccountService, TransferService, TransactionRecorder (business logic)
├── security/                     # JWT filter/service, user details, security config, login rate limiter
├── event/                        # async post-commit audit events
└── exception/                    # domain exceptions + global handler

src/main/resources
├── application.yml               # config (env-var driven)
└── db/migration/                 # Flyway migrations (V1 schema, V2 demo seed, V3 sequence sync + idempotency)

src/test/java/com/wallet/wallet
└── service/TransferServiceConcurrencyTest.java   # concurrency proof

Dockerfile                        # multi-stage Gradle build → slim JRE image
docker-compose.yml                # app + PostgreSQL, one command
```

---

## How it works (request flow)

1. Client logs in via `/auth/login` — Spring Security verifies the credentials and the `JwtService` signs a JWT containing the username and role.
2. Client sends the token as `Authorization: Bearer <token>` — `JwtAuthenticationFilter` validates the signature/expiry on every request and populates the security context.
3. The controller validates the DTO (`@Valid` → 400 on bad input) and delegates to the service.
4. The service runs in a transaction: locks the rows, applies business rules, saves, and returns the updated state.
5. After commit, an async audit event is logged.
6. Any domain error (not found, blocked, insufficient balance…) is translated by `GlobalExceptionHandler` into a consistent JSON error with the right status code.
