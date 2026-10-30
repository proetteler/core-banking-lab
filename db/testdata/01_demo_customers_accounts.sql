-- Demo-Daten für Entwicklung und Smoke-Test. Nicht Teil von install.sql, einmalig laden.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

INSERT INTO customer (customer_number, customer_name) VALUES ('K-1001', 'Anna Muster');
INSERT INTO customer (customer_number, customer_name) VALUES ('K-1002', 'Ben Beispiel');
INSERT INTO customer (customer_number, customer_name) VALUES ('K-1003', 'Clara Test');

-- Anna: CHF-Konto und EUR-Konto
INSERT INTO account (account_number, account_type, account_name, customer_id, currency_code)
SELECT 'CUS-1001-01', 'CUSTOMER', 'Privatkonto CHF', customer_id, 'CHF'
FROM   customer WHERE customer_number = 'K-1001';

INSERT INTO account (account_number, account_type, account_name, customer_id, currency_code)
SELECT 'CUS-1001-02', 'CUSTOMER', 'Fremdwährungskonto EUR', customer_id, 'EUR'
FROM   customer WHERE customer_number = 'K-1001';

-- Ben: CHF-Konto und gesperrtes CHF-Konto (Belastungssperre)
INSERT INTO account (account_number, account_type, account_name, customer_id, currency_code)
SELECT 'CUS-1002-01', 'CUSTOMER', 'Privatkonto CHF', customer_id, 'CHF'
FROM   customer WHERE customer_number = 'K-1002';

INSERT INTO account (account_number, account_type, account_name, customer_id, currency_code, status)
SELECT 'CUS-1002-02', 'CUSTOMER', 'Sparkonto gesperrt', customer_id, 'CHF', 'BLOCKED_DEBIT'
FROM   customer WHERE customer_number = 'K-1002';

-- Clara: CHF-Konto mit Kreditlimite 500
INSERT INTO account (account_number, account_type, account_name, customer_id, currency_code, overdraft_limit)
SELECT 'CUS-1003-01', 'CUSTOMER', 'Privatkonto CHF mit Limite', customer_id, 'CHF', 500
FROM   customer WHERE customer_number = 'K-1003';

COMMIT;
