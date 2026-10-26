CREATE TABLE customer (
  customer_id      NUMBER GENERATED ALWAYS AS IDENTITY,
  customer_number  VARCHAR2(20 CHAR)  NOT NULL,
  customer_name    VARCHAR2(100 CHAR) NOT NULL,
  created_at       TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT customer_pk        PRIMARY KEY (customer_id),
  CONSTRAINT customer_number_uk UNIQUE (customer_number)
);