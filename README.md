# Core Banking Lab

A self-study project that rebuilds the core mechanics of a core banking system: customers, accounts, double-entry bookkeeping, payments and end-of-day processing. It is built on Oracle and PL/SQL, with REST, Kafka and Kubernetes integration added later.

> **Disclaimer:** This is a personal learning project. It does not contain any Avaloq code or proprietary vendor internals. It only models publicly known banking and software concepts.

## Status and roadmap

This is a self-study project to prepare for integration roles around Avaloq. It contains no Avaloq code, only publicly known concepts. Parts of it were created with AI assistance, and I am rebuilding them step by step myself.

**Done**
- PL/SQL booking logic: double-entry bookkeeping, funds check, account locks, error concept
- REST service with OpenAPI (Swagger UI)
- Kafka: transactional outbox for booking events, payment orders with results and dead-letter topic
- Least privilege: the service user can only execute the packages
- Health and monitoring: health endpoint, outbox backlog

**In progress**
- Deployment on Kubernetes / OpenShift

**Planned**
- CI with GitHub Actions
- Architecture documentation (arc42, ADRs)
- Authentication (OAuth2)
- utPLSQL tests
- End-of-day processing

## Goals

- Understand how a core banking system keeps money correct: every booking is balanced (debit = credit), and nothing is deleted, only reversed.
- Practice PL/SQL, transactions and testing on a real Oracle database.
- Practice integration patterns: REST/OpenAPI, Kafka (outbox pattern), containers and Kubernetes/OpenShift.

## Tech stack

| Area | Technology |
|---|---|
| Database | Oracle Database Free 23.26 (`gvenzl/oracle-free`) |
| Core logic | PL/SQL packages, tested with utPLSQL |
| Runtime | Docker Desktop, Docker Compose |
| Planned | REST API (OpenAPI), Kafka, Kubernetes, OpenShift |

## Prerequisites

- Docker Desktop (Apple Silicon / ARM64 supported)
- A SQL client, e.g. VS Code with the Oracle SQL Developer extension

## Quick start

```bash
# 1. Create your local environment file and set your own passwords
cp .env.example .env

# 2. Start the database (the first start takes a few minutes)
docker compose up -d

# 3. Wait until the database is ready
docker compose logs oracle | grep "DATABASE IS READY TO USE"

# 4. Create the database user of the integration service (as SYSTEM, once)
docker exec -it -w /opt/cbl/db cbl-oracle bash -c 'sqlplus -s "system/$ORACLE_PASSWORD@//localhost/FREEPDB1" @security/01_api_user.sql "$API_USER_PASSWORD"'

# 5. Install schema, seed data, packages and grants
docker exec -it -w /opt/cbl/db cbl-oracle bash -c 'sqlplus -s "$APP_USER/$APP_USER_PASSWORD@//localhost/FREEPDB1" @install.sql'
```

The `db/` folder is mounted read-only into the container, and `install.sql` runs all scripts in order, stops at the first error and fails if any PL/SQL object is invalid. Step 4 is separate because `CORE_BANK` is not allowed to create users; `install.sql` then grants `CORE_BANK_API` only `EXECUTE` on the packages.

### Demo data and smoke test

```bash
# Load demo customers and accounts (once; not idempotent)
docker exec -it -w /opt/cbl/db cbl-oracle bash -c 'sqlplus -s "$APP_USER/$APP_USER_PASSWORD@//localhost/FREEPDB1" @testdata/01_demo_customers_accounts.sql'

# Run the smoke test (changes nothing, exits with code 1 on failure)
docker exec -it -w /opt/cbl/db cbl-oracle bash -c 'sqlplus -s "$APP_USER/$APP_USER_PASSWORD@//localhost/FREEPDB1" @tests/smoke_test.sql'
```

### Connect

| Setting | Value |
|---|---|
| Host | `localhost` |
| Port | `1521` |
| Service name | `FREEPDB1` |
| User | value of `APP_USER` in `.env` (default `CORE_BANK`) |
| Password | value of `APP_USER_PASSWORD` in `.env` |

### Stop and reset

```bash
docker compose down      # stop the container, keep the data
docker compose down -v   # stop the container and DELETE all data (fresh start)
```

After `down -v`, repeat steps 2 to 5 of the quick start to rebuild everything from scratch.

## Project structure

```
.
├── db/
│   ├── install.sql      # runs all scripts below in order, stops on errors or invalid objects
│   ├── data/            # seed data (internal bank accounts)
│   │   ├── 01_internal_accounts.sql
│   │   └── 02_internal_accounts_eur.sql
│   ├── packages/        # PL/SQL packages (spec .pks, body .pkb)
│   │   ├── bank_errors.pks    # error numbers and named exceptions
│   │   ├── account_api.pks / .pkb
│   │   ├── booking_api.pks / .pkb   # deposit, transfer
│   │   └── outbox_api.pks / .pkb    # read and mark outbox events, backlog
│   ├── testdata/        # demo customers and accounts, loaded separately
│   │   └── 01_demo_customers_accounts.sql
│   ├── security/        # database user of the integration service
│   │   ├── 01_api_user.sql    # creates CORE_BANK_API (run as SYSTEM, separate call)
│   │   └── 02_api_grants.sql  # EXECUTE on the packages only (part of install.sql)
│   ├── tests/
│   │   └── smoke_test.sql   # prints SMOKE TEST OK, rolls back, exit code 1 on failure
│   └── ddl/             # table definitions, run in numeric order
│       ├── 01_customer.sql
│       ├── 02_account.sql
│       ├── 03_booking.sql
│       ├── 04_booking_line.sql
│       └── 05_outbox_event.sql
├── service/             # Spring Boot integration service (REST, Kafka outbox publisher, payment order consumer)
├── api/
│   └── asyncapi.yaml    # Kafka topics and message schemas (AsyncAPI 3)
├── docker-compose.yml   # Oracle Database Free, Kafka, Kafka UI and the integration service
├── .env.example         # required environment variables (no secrets)
└── CLAUDE.md            # working agreements for the AI coding assistant
```

### Data model

```
customer 1 ── n account 1 ── n booking_line n ── 1 booking
```

- `account`: customer accounts and internal bank accounts (e.g. cash, fee income)
- `booking`: booking header (type, booking date, value date, unique reference, reversal link)
- `booking_line`: one line per account, signed amount (debit +, credit −). The lines of a booking sum to 0.

## Integration service

A Spring Boot 3 / Java 21 service in `service/` that exposes the PL/SQL packages as a REST API. It contains no banking rules: it validates the request format, calls `booking_api` and `account_api` over JDBC and translates errors. Maven and the JDK are only used inside the build container, nothing has to be installed on the host.

### Start

```bash
# needs API_USER_PASSWORD in .env and the database user from quick start step 4
docker compose up -d --build     # builds the image (runs mvn verify), starts Oracle, Kafka and the service
curl -s http://127.0.0.1:8080/actuator/health
```

- Swagger UI: <http://127.0.0.1:8080/swagger-ui.html>, OpenAPI JSON: <http://127.0.0.1:8080/v3/api-docs>
- The port is bound to `127.0.0.1` only. There is **no authentication yet**, so do not expose it.
- Load the demo data first (see "Demo data and smoke test"), the examples use its accounts.

### Examples

```bash
# balance
curl -s http://127.0.0.1:8080/accounts/CUS-1001-01/balance

# deposit
curl -s -X POST http://127.0.0.1:8080/deposits -H 'Content-Type: application/json' \
  -d '{"accountNumber":"CUS-1001-01","amount":1000.00,"currency":"CHF","reference":"DEP-0001"}'

# transfer
curl -s -X POST http://127.0.0.1:8080/transfers -H 'Content-Type: application/json' \
  -d '{"fromAccountNumber":"CUS-1001-01","toAccountNumber":"CUS-1002-01","amount":300.00,"currency":"CHF","reference":"TRF-0001"}'
```

The `reference` is unique. Sending the same one again returns 409, so a client can safely retry after a timeout without booking twice.

### Error codes

Errors are returned as `application/problem+json` (RFC 9457) with the fields `type`, `status`, `detail` and `errorCode`. The mapping is based on the error numbers of the package `bank_errors` and lives in one place (`GlobalExceptionHandler`).

| HTTP | `errorCode` | Cause |
|---|---|---|
| 400 | (validation) | Malformed request: missing field, amount ≤ 0 or more than 2 decimals, currency not 3 capital letters. Rejected before the database is called |
| 404 | `ACCOUNT_NOT_FOUND` | Unknown account number |
| 409 | `DUPLICATE_REFERENCE` | A booking with this reference exists |
| 422 | `ACCOUNT_BLOCKED` | Debit on a blocked account, or any booking on a closed account |
| 422 | `INSUFFICIENT_FUNDS` | Balance plus overdraft limit does not cover the amount |
| 422 | `CURRENCY_MISMATCH` | Currency differs from the account currency |
| 422 | `INVALID_AMOUNT`, `SAME_ACCOUNT` | Rules checked again in the database for other callers |
| 500 | (none) | Anything else. The response has no internal details, the cause is only in the log |

The `detail` text is fixed per error. The database message is not returned, because it can contain balances.

### Design decisions

- **Account numbers, not internal IDs.** The API identifies accounts by `accountNumber`, the business key. The internal ID is a surrogate key of the database. If it leaked, clients would depend on it and it could be guessed. The service resolves the number with `account_api.get_account_id` in the same transaction as the booking, `booking_api` keeps working with IDs.
- **Transaction in the service.** `booking_api` never commits. `@Transactional` in `BookingService` commits on success and rolls back on any error, so the account lookup, the booking and the outbox event are one unit.
- **Least privilege.** The service connects as `CORE_BANK_API`, which can log in and execute the two packages, nothing else. It cannot read or write a single table directly, so every change passes the checks in the packages.

## Kafka

The stack contains a single-node Kafka (KRaft, no ZooKeeper) and Kafka UI. Bookings leave the database as events, payment orders come in as messages. `docker compose up -d` starts everything; `docker compose up -d oracle` still starts only the database. Kafka uses PLAINTEXT without login and has no host port, so it is for the lab only.

- Kafka UI: <http://127.0.0.1:8081>
- Contract of all topics: [`api/asyncapi.yaml`](api/asyncapi.yaml) (AsyncAPI 3.0)

| Topic | Direction | Key | Content |
|---|---|---|---|
| `core-banking.booking-events` | service → out | `bookingReference` | `BOOKING_CREATED` for each committed booking |
| `core-banking.payment-orders` | in → service | free | order: `reference`, `fromAccountNumber`, `toAccountNumber`, `amount`, `currency` |
| `core-banking.payment-results` | service → out | `reference` | `BOOKED`, `DUPLICATE` or `REJECTED` per order |
| `core-banking.payment-orders.DLT` | service → ops | as original | orders that could not be processed (dead letters) |

All topics have 3 partitions and 1 replica. Messages with the same key stay in one partition and therefore in order.

### Booking events (transactional outbox)

`booking_api` writes the event into `outbox_event` in the same transaction as the booking, so there is never a booking without an event or an event without a booking. The `OutboxPublisher` in the service reads unpublished events in order (`outbox_api.fetch_unpublished`, `FOR UPDATE SKIP LOCKED`), sends them with `acks=all`, waits for the acknowledgement and only then marks them as published and commits.

```json
{"eventId":"5f0c7fd6-22a9-0b68-e063-020014ac421e","eventType":"BOOKING_CREATED","schemaVersion":1,
 "occurredAt":"2026-10-30T09:34:14.282Z","bookingReference":"DEP-0001","bookingType":"DEPOSIT",
 "debitAccountNumber":"INT-1000","creditAccountNumber":"CUS-1001-01","amount":100,"currency":"CHF"}
```

The events contain business keys only (reference, account numbers), never internal IDs.

**At-least-once, not exactly-once.** If the service dies after sending but before the commit, the same event is sent again. **Consumers must be idempotent and use `eventId`** (it is stored in the payload, so it is identical on every redelivery). There is also no global order across bookings, only per key.

```bash
# read the events
docker exec cbl-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic core-banking.booking-events --from-beginning --property print.key=true
```

### Payment orders

The service reads `core-banking.payment-orders` and books each order through the same service code as `POST /transfers` (one transaction). Every order gets a result on `core-banking.payment-results`:

| Result | Meaning | Retry |
|---|---|---|
| `BOOKED` | transfer booked | |
| `DUPLICATE` | the reference is already booked, nothing booked again | |
| `REJECTED` + `errorCode` | business rule failed: `INSUFFICIENT_FUNDS`, `ACCOUNT_BLOCKED`, `CURRENCY_MISMATCH`, `ACCOUNT_NOT_FOUND`, `INVALID_AMOUNT`, `SAME_ACCOUNT`, or `INVALID_ORDER` (fields missing or invalid) | no, the answer would be the same |
| technical error (database down, timeout) | no result yet | 3 retries (1 s, 2 s, 4 s), then the order goes to `...payment-orders.DLT` |
| not JSON, or no `reference` | | no, straight to the DLT, no result |

The `reference` makes the processing idempotent, so redelivery is harmless. One edge case: if the booking is committed but sending the result fails, the retry answers `DUPLICATE` instead of `BOOKED`. So `DUPLICATE` means "this reference is booked". A result that is never lost would have to go through the outbox as well.

```bash
# send an order (key|value)
echo 'ORD-0001|{"reference":"ORD-0001","fromAccountNumber":"CUS-1001-01","toAccountNumber":"CUS-1002-01","amount":50.00,"currency":"CHF"}' | \
  docker exec -i cbl-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
    --topic core-banking.payment-orders --property parse.key=true --property key.separator='|'

# read the results
docker exec cbl-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic core-banking.payment-results --from-beginning --property print.key=true
```

### Monitoring: Kafka is not a hard dependency

If Kafka is down, bookings via REST still work (payment orders cannot arrive, of course, and are processed once Kafka is back): the events wait in `outbox_event`, the publisher logs a warning every few seconds and delivers the backlog once Kafka is back. The health status of the service therefore stays `UP`.

Instead of reporting the service as down, the backlog is monitored. It is visible at `GET /actuator/health/outbox` (the details are shown for this group only):

```json
{"status":"UP","components":{"outboxBacklog":{"status":"UP","details":{"unpublishedCount":1,"oldestAgeSeconds":14,"lagging":false,"warnAfterSeconds":300}}}}
```

Why not `DOWN`: a health check that fails on Kafka would make an orchestrator restart or remove a service that is perfectly able to book. That would turn a delay in the event stream into an outage of the core function. An alert on `oldestAgeSeconds` (here `lagging` once it exceeds 300 s) points at the real problem without that side effect. The database functions behind it are `outbox_api.unpublished_count` and `outbox_api.oldest_unpublished_age_seconds`.

## Roadmap

- [x] 1. Setup: Oracle container, connection from VS Code
- [x] 2. Architecture frame (arc42): context, scope, quality goals
- [x] 3. Data model: customers, accounts, bookings, double-entry bookkeeping
- [ ] 4. PL/SQL basics: packages, procedures, exceptions, transactions
- [ ] 5. Booking logic: deposit, transfer, funds check, account locks, fees
- [ ] 6. Tests with utPLSQL
- [ ] 7. End of day: interest accrual, period-end booking
- [ ] 8. Payments: import ISO 20022 pain.001, error handling per payment
- [ ] 9. Integration service: REST API with OpenAPI
- [ ] 10. Kafka: outbox pattern, bookings as events
- [ ] 11. Kubernetes, then OpenShift

## Documentation

The architecture documentation (arc42, ADRs) and the learning notes are kept separately in a personal knowledge base and are not part of this repository.
