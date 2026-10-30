-- Legt den Datenbank-User für den Integrationsservice an.
-- Läuft als SYSTEM, weil CORE_BANK keine User anlegen darf. Nicht Teil von install.sql.
-- Aufruf: sqlplus system/...@//localhost/FREEPDB1 @security/01_api_user.sql <passwort>
--
-- Least Privilege: Der Service darf sich nur anmelden und die Packages aufrufen
-- (Grants in 02_api_grants.sql). Er hat keinen Zugriff auf die Tabellen. Jede Änderung
-- läuft damit zwingend durch die Banklogik (Prüfungen, Doppelbuchung, Outbox).
-- Die Packages laufen mit den Rechten des Besitzers CORE_BANK (Definer Rights).
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET VERIFY OFF
DEFINE api_password = &1

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM dba_users WHERE username = 'CORE_BANK_API';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER core_bank_api IDENTIFIED BY "&api_password"';
    ELSE
        -- Wiederholbar: bestehender User bekommt nur das Passwort neu gesetzt
        EXECUTE IMMEDIATE 'ALTER USER core_bank_api IDENTIFIED BY "&api_password"';
    END IF;
END;
/

GRANT CREATE SESSION TO core_bank_api;

UNDEFINE api_password
EXIT
