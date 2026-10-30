-- Kasse in EUR, damit Einzahlungen in EUR ein Gegenkonto in derselben Währung haben
INSERT INTO account (account_number, account_type, account_name, currency_code)
VALUES ('INT-1001', 'INTERNAL', 'Kasse EUR', 'EUR');

COMMIT;
