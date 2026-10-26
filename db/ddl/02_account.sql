CREATE TABLE account (
  account_id       NUMBER GENERATED ALWAYS AS IDENTITY,
  account_number   VARCHAR2(20 CHAR)  NOT NULL,
  iban             VARCHAR2(34 CHAR),
  account_type     VARCHAR2(20 CHAR)  NOT NULL,
  account_name     VARCHAR2(100 CHAR) NOT NULL,
  customer_id      NUMBER,
  currency_code    CHAR(3)            NOT NULL,
  balance          NUMBER(18,2)       DEFAULT 0 NOT NULL,
  overdraft_limit  NUMBER(18,2)       DEFAULT 0 NOT NULL,
  status           VARCHAR2(20 CHAR)  DEFAULT 'OPEN' NOT NULL,
  opened_date      DATE               DEFAULT TRUNC(SYSDATE) NOT NULL,
  closed_date      DATE,
  CONSTRAINT account_pk          PRIMARY KEY (account_id),
  CONSTRAINT account_number_uk   UNIQUE (account_number),
  CONSTRAINT account_iban_uk     UNIQUE (iban),
  CONSTRAINT account_customer_fk FOREIGN KEY (customer_id)
    REFERENCES customer (customer_id),
  CONSTRAINT account_type_ck     CHECK (account_type IN ('CUSTOMER', 'INTERNAL')),
  CONSTRAINT account_status_ck   CHECK (status IN ('OPEN', 'BLOCKED_DEBIT', 'CLOSED')),
  CONSTRAINT account_owner_ck    CHECK (
       (account_type = 'CUSTOMER' AND customer_id IS NOT NULL)
    OR (account_type = 'INTERNAL' AND customer_id IS NULL)
  ),
  CONSTRAINT account_currency_ck CHECK (REGEXP_LIKE(currency_code, '^[A-Z]{3}$')),
  CONSTRAINT account_limit_ck    CHECK (overdraft_limit >= 0),
  CONSTRAINT account_closed_ck   CHECK (closed_date IS NULL OR closed_date >= opened_date)
);