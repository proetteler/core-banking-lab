CREATE OR REPLACE PACKAGE booking_api
AS
    -- Alle Buchungen: kein COMMIT/ROLLBACK im Package, die Transaktion steuert der Aufrufer.
    -- Fehler werden mit RAISE_APPLICATION_ERROR gemeldet (Nummern in bank_errors).

    -- Einzahlung: Soll Kasse (intern), Haben Kundenkonto
    FUNCTION deposit (
        p_account_id IN account.account_id%TYPE,
        p_amount     IN booking_line.amount%TYPE,
        p_currency   IN account.currency_code%TYPE,
        p_reference  IN booking.reference%TYPE
    ) RETURN booking.booking_id%TYPE;

    -- Überweisung: Soll Auftraggeber, Haben Empfänger
    FUNCTION transfer (
        p_from_account_id IN account.account_id%TYPE,
        p_to_account_id   IN account.account_id%TYPE,
        p_amount          IN booking_line.amount%TYPE,
        p_currency        IN account.currency_code%TYPE,
        p_reference       IN booking.reference%TYPE
    ) RETURN booking.booking_id%TYPE;
END booking_api;
/
