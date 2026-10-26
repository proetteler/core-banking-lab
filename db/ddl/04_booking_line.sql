CREATE TABLE booking_line (
  booking_line_id  NUMBER GENERATED ALWAYS AS IDENTITY,
  booking_id       NUMBER        NOT NULL,
  account_id       NUMBER        NOT NULL,
  amount           NUMBER(18,2)  NOT NULL,
  CONSTRAINT booking_line_pk         PRIMARY KEY (booking_line_id),
  CONSTRAINT booking_line_booking_fk FOREIGN KEY (booking_id)
    REFERENCES booking (booking_id),
  CONSTRAINT booking_line_account_fk FOREIGN KEY (account_id)
    REFERENCES account (account_id),
  CONSTRAINT booking_line_amount_ck  CHECK (amount <> 0)
);

CREATE INDEX booking_line_booking_ix ON booking_line (booking_id);
CREATE INDEX booking_line_account_ix ON booking_line (account_id);
