-- Core Banking Lab: Installation von Schema, Stammdaten und Packages
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR  EXIT FAILURE ROLLBACK

PROMPT === Tabellen ===
@@ddl/01_customer.sql
@@ddl/02_account.sql
@@ddl/03_booking.sql
@@ddl/04_booking_line.sql

PROMPT === Stammdaten ===
@@data/01_internal_accounts.sql

PROMPT === Packages ===
@@packages/account_api.pks
@@packages/account_api.pkb

PROMPT === Prüfung: ungültige Objekte ===
DECLARE
    v_invalid NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO   v_invalid
    FROM   user_objects
    WHERE  status = 'INVALID';

    IF v_invalid > 0 THEN
        RAISE_APPLICATION_ERROR(-20000, v_invalid || ' ungültige Objekte, siehe USER_ERRORS');
    END IF;
END;
/

PROMPT === Installation abgeschlossen ===
EXIT