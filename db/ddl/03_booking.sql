CREATE TABLE booking (
  booking_id    NUMBER GENERATED ALWAYS AS IDENTITY,
  booking_type  VARCHAR2(20 CHAR)  NOT NULL,
  booking_date  DATE               DEFAULT TRUNC(SYSDATE) NOT NULL,
  value_date    DATE               NOT NULL,
  reference     VARCHAR2(50 CHAR)  NOT NULL,
  reversal_of   NUMBER,
  created_at    TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT booking_pk           PRIMARY KEY (booking_id),
  CONSTRAINT booking_reference_uk UNIQUE (reference),
  CONSTRAINT booking_reversal_uk  UNIQUE (reversal_of),
  CONSTRAINT booking_reversal_fk  FOREIGN KEY (reversal_of)
    REFERENCES booking (booking_id),
  CONSTRAINT booking_type_ck      CHECK (booking_type IN
    ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'FEE', 'INTEREST', 'REVERSAL')),
  CONSTRAINT booking_reversal_ck  CHECK (
       (booking_type = 'REVERSAL'  AND reversal_of IS NOT NULL)
    OR (booking_type <> 'REVERSAL' AND reversal_of IS NULL)
  ),
  CONSTRAINT booking_dates_ck     CHECK (
        booking_date = TRUNC(booking_date)
    AND value_date   = TRUNC(value_date)
  )
);