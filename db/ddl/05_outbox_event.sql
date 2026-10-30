-- Outbox: Events werden in derselben Transaktion wie die Buchung geschrieben.
-- Ein separater Prozess liest unveröffentlichte Events und sendet sie an Kafka.
CREATE TABLE outbox_event (
  outbox_event_id  NUMBER GENERATED ALWAYS AS IDENTITY,
  aggregate_type   VARCHAR2(50 CHAR) NOT NULL,
  aggregate_id     VARCHAR2(50 CHAR) NOT NULL,
  event_type       VARCHAR2(50 CHAR) NOT NULL,
  payload          CLOB              NOT NULL,
  created_at       TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
  published_at     TIMESTAMP,
  CONSTRAINT outbox_event_pk         PRIMARY KEY (outbox_event_id),
  CONSTRAINT outbox_event_payload_ck CHECK (payload IS JSON)
);

-- Abfrage "unveröffentlichte Events in Reihenfolge":
--   WHERE published_at IS NULL ORDER BY outbox_event_id
-- NULL in published_at steht im Index, weil outbox_event_id NOT NULL ist.
CREATE INDEX outbox_event_unpublished_ix ON outbox_event (published_at, outbox_event_id);
