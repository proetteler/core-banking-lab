CREATE OR REPLACE PACKAGE BODY outbox_api
AS
    c_max_batch CONSTANT PLS_INTEGER := 1000;

    FUNCTION fetch_unpublished (
        p_batch_size IN PLS_INTEGER
    ) RETURN SYS_REFCURSOR
    IS
        v_limit      PLS_INTEGER := LEAST(GREATEST(NVL(p_batch_size, 1), 1), c_max_batch);
        v_candidates SYS.ODCINUMBERLIST;
        v_result     SYS_REFCURSOR;
    BEGIN
        -- Schritt 1: die ältesten Kandidaten ohne Sperre bestimmen.
        -- FOR UPDATE sperrt beim OPEN alle gewählten Zeilen, FETCH FIRST ist damit nicht kombinierbar
        -- (ORA-02014). Ohne diese Vorauswahl würde ein grosser Rückstand komplett gesperrt.
        SELECT outbox_event_id
        BULK COLLECT INTO v_candidates
        FROM   outbox_event
        WHERE  published_at IS NULL
        ORDER BY outbox_event_id
        FETCH FIRST v_limit ROWS ONLY;

        -- Schritt 2: genau diese Zeilen sperren, von anderen Publishern gesperrte überspringen.
        -- published_at wird erneut geprüft, falls ein anderer Publisher dazwischen markiert hat.
        OPEN v_result FOR
            SELECT outbox_event_id, aggregate_type, aggregate_id, event_type, payload, created_at
            FROM   outbox_event
            WHERE  outbox_event_id IN (SELECT column_value FROM TABLE(v_candidates))
            AND    published_at IS NULL
            ORDER BY outbox_event_id
            FOR UPDATE SKIP LOCKED;

        RETURN v_result;
    END fetch_unpublished;

    PROCEDURE mark_published (
        p_outbox_event_id IN outbox_event.outbox_event_id%TYPE
    )
    IS
    BEGIN
        UPDATE outbox_event
        SET    published_at = SYSTIMESTAMP
        WHERE  outbox_event_id = p_outbox_event_id
        AND    published_at IS NULL;

        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_outbox_event_not_found,
                'Outbox-Event ' || p_outbox_event_id || ' nicht gefunden oder schon veröffentlicht');
        END IF;
    END mark_published;

    FUNCTION unpublished_count RETURN NUMBER
    IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM outbox_event WHERE published_at IS NULL;
        RETURN v_count;
    END unpublished_count;

    FUNCTION oldest_unpublished_age_seconds RETURN NUMBER
    IS
        v_age NUMBER;
    BEGIN
        -- created_at wird aus SYSTIMESTAMP befüllt, darum der Vergleich ebenfalls mit SYSTIMESTAMP
        SELECT ROUND((CAST(CAST(SYSTIMESTAMP AS TIMESTAMP) AS DATE) - CAST(MIN(created_at) AS DATE)) * 86400)
        INTO   v_age
        FROM   outbox_event
        WHERE  published_at IS NULL;

        RETURN v_age;
    END oldest_unpublished_age_seconds;

END outbox_api;
/
