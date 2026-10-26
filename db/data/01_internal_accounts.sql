INSERT INTO account (account_number, account_type, account_name, currency_code)
VALUES ('INT-1000', 'INTERNAL', 'Kasse CHF', 'CHF');

INSERT INTO account (account_number, account_type, account_name, currency_code)
VALUES ('INT-2000', 'INTERNAL', 'Clearing CHF', 'CHF');

INSERT INTO account (account_number, account_type, account_name, currency_code)
VALUES ('INT-4000', 'INTERNAL', 'Gebührenertrag CHF', 'CHF');

INSERT INTO account (account_number, account_type, account_name, currency_code)
VALUES ('INT-6000', 'INTERNAL', 'Zinsaufwand CHF', 'CHF');

COMMIT;