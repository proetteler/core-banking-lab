# Core Banking Lab

A self-study project that rebuilds the core mechanics of a core banking system: customers, accounts, double-entry bookkeeping, payments and end-of-day processing. It is built on Oracle and PL/SQL, with REST, Kafka and Kubernetes integration added later.

> **Disclaimer:** This is a personal learning project. It does not contain any Avaloq code or proprietary vendor internals. It only models publicly known banking and software concepts.

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
│   │   └── booking_api.pks / .pkb   # deposit, transfer
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
├── service/             # Spring Boot integration service (REST in front of the packages)
├── docker-compose.yml   # Oracle Database Free and the integration service
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
docker compose up -d --build     # builds the image (runs mvn verify), starts Oracle and the service
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
