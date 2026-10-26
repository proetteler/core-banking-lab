

-- Core Banking Lab: Installation von Schema und Stammdaten
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR  EXIT FAILURE ROLLBACK

PROMPT === Tabellen ===
@@ddl/01_customer.sql
@@ddl/02_account.sql
@@ddl/03_booking.sql
@@ddl/04_booking_line.sql

PROMPT === Stammdaten ===
@@data/01_internal_accounts.sql

PROMPT === Installation abgeschlossen ===
EXIT
