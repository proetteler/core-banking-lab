-- Rechte für CORE_BANK_API, vergeben vom Besitzer CORE_BANK (Teil von install.sql).
-- Voraussetzung: 01_api_user.sql wurde als SYSTEM ausgeführt, sonst ORA-01917.
-- Nur EXECUTE auf die Packages, kein SELECT/INSERT/UPDATE auf Tabellen (Least Privilege).
GRANT EXECUTE ON booking_api TO core_bank_api;
GRANT EXECUTE ON account_api TO core_bank_api;
