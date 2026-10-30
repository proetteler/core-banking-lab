CREATE OR REPLACE PACKAGE outbox_api
AS
    -- Schnittstelle für den Outbox-Publisher des Integrationsservice.
    -- Kein COMMIT/ROLLBACK im Package: Sperren und Markierung gelten bis zum Commit des Aufrufers.

    -- Holt bis zu p_batch_size unveröffentlichte Events in ID-Reihenfolge und sperrt sie
    -- (FOR UPDATE SKIP LOCKED: ein zweiter Publisher überspringt gesperrte Zeilen).
    -- p_batch_size wird auf 1..1000 begrenzt.
    -- Spalten: outbox_event_id, aggregate_type, aggregate_id, event_type, payload, created_at
    FUNCTION fetch_unpublished (
        p_batch_size IN PLS_INTEGER
    ) RETURN SYS_REFCURSOR;

    -- Markiert ein Event als veröffentlicht; unbekannt oder schon veröffentlicht -> outbox_event_not_found
    PROCEDURE mark_published (
        p_outbox_event_id IN outbox_event.outbox_event_id%TYPE
    );

    -- Rückstand für das Monitoring: Anzahl unveröffentlichter Events
    FUNCTION unpublished_count RETURN NUMBER;

    -- Alter des ältesten unveröffentlichten Events in Sekunden, NULL wenn kein Rückstand
    FUNCTION oldest_unpublished_age_seconds RETURN NUMBER;
END outbox_api;
/
