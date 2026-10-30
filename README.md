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

# 4. Install schema and seed data
docker exec -it -w /opt/cbl/db cbl-oracle bash -c 'sqlplus -s "$APP_USER/$APP_USER_PASSWORD@//localhost/FREEPDB1" @install.sql'
```

The `db/` folder is mounted read-only into the container, and `install.sql` runs all scripts in order, stops at the first error and fails if any PL/SQL object is invalid.

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

After `down -v`, repeat steps 2 to 4 of the quick start to rebuild everything from scratch.

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
│   ├── tests/
│   │   └── smoke_test.sql   # prints SMOKE TEST OK, rolls back, exit code 1 on failure
│   └── ddl/             # table definitions, run in numeric order
│       ├── 01_customer.sql
│       ├── 02_account.sql
│       ├── 03_booking.sql
│       ├── 04_booking_line.sql
│       └── 05_outbox_event.sql
├── docker-compose.yml   # Oracle Database Free container
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
