CREATE OR REPLACE PACKAGE BODY booking_api
AS
    -- Konvention: booking_line.amount Soll +, Haben -; Summe pro Buchung = 0.
    -- account.balance ist der Haben-Saldo, also -SUM(booking_line.amount):
    --   Haben (Gutschrift) erhöht den Saldo, Soll (Belastung) senkt ihn.

    -- Präfix der internen Kassenkonten (INT-1000 CHF, INT-1001 EUR)
    c_cash_pattern CONSTANT VARCHAR2(10) := 'INT-1%';

    PROCEDURE check_amount (
        p_amount IN booking_line.amount%TYPE
    )
    IS
    BEGIN
        IF p_amount IS NULL OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_invalid_amount,
                'Betrag muss > 0 sein und höchstens 2 Nachkommastellen haben');
        END IF;
    END check_amount;

    -- Sperrt eine Kontozeile (FOR UPDATE) und liefert sie zurück
    FUNCTION lock_account (
        p_account_id IN account.account_id%TYPE
    ) RETURN account%ROWTYPE
    IS
        v_account account%ROWTYPE;
    BEGIN
        SELECT *
        INTO   v_account
        FROM   account
        WHERE  account_id = p_account_id
        FOR UPDATE;

        RETURN v_account;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_account_not_found,
                'Konto-ID ' || p_account_id || ' nicht gefunden');
    END lock_account;

    -- Sperrt zwei Konten immer in aufsteigender ID-Reihenfolge (gegen Deadlocks),
    -- egal in welcher Rolle sie der Aufrufer übergibt.
    PROCEDURE lock_accounts (
        p_first_id  IN  account.account_id%TYPE,
        p_second_id IN  account.account_id%TYPE,
        p_first     OUT account%ROWTYPE,
        p_second    OUT account%ROWTYPE
    )
    IS
    BEGIN
        IF p_first_id < p_second_id THEN
            p_first  := lock_account(p_first_id);
            p_second := lock_account(p_second_id);
        ELSE
            p_second := lock_account(p_second_id);
            p_first  := lock_account(p_first_id);
        END IF;
    END lock_accounts;

    -- Belastung nur auf OPEN; BLOCKED_DEBIT und CLOSED sind gesperrt
    PROCEDURE check_can_debit (
        p_account IN account%ROWTYPE
    )
    IS
    BEGIN
        IF p_account.status <> 'OPEN' THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_account_blocked,
                'Konto ' || p_account.account_number || ' ist für Belastungen gesperrt (' || p_account.status || ')');
        END IF;
    END check_can_debit;

    -- Gutschrift ausser auf CLOSED immer erlaubt (auch auf BLOCKED_DEBIT)
    PROCEDURE check_can_credit (
        p_account IN account%ROWTYPE
    )
    IS
    BEGIN
        IF p_account.status = 'CLOSED' THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_account_blocked,
                'Konto ' || p_account.account_number || ' ist geschlossen');
        END IF;
    END check_can_credit;

    PROCEDURE check_currency (
        p_account  IN account%ROWTYPE,
        p_currency IN account.currency_code%TYPE
    )
    IS
    BEGIN
        IF p_currency IS NULL OR p_account.currency_code <> p_currency THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_currency_mismatch,
                'Konto ' || p_account.account_number || ' führt ' || p_account.currency_code
                || ', Buchung lautet auf ' || NVL(p_currency, 'NULL'));
        END IF;
    END check_currency;

    -- Schreibt Kopf, Soll- und Haben-Zeile, aktualisiert beide Salden und schreibt das Outbox-Event.
    -- Setzt voraus, dass beide Konten bereits gesperrt und geprüft sind.
    FUNCTION post_booking (
        p_booking_type IN booking.booking_type%TYPE,
        p_debit        IN account%ROWTYPE,
        p_credit       IN account%ROWTYPE,
        p_amount       IN booking_line.amount%TYPE,
        p_currency     IN account.currency_code%TYPE,
        p_reference    IN booking.reference%TYPE
    ) RETURN booking.booking_id%TYPE
    IS
        v_booking_id booking.booking_id%TYPE;
    BEGIN
        BEGIN
            INSERT INTO booking (booking_type, value_date, reference)
            VALUES (p_booking_type, TRUNC(SYSDATE), p_reference)
            RETURNING booking_id INTO v_booking_id;
        EXCEPTION
            WHEN DUP_VAL_ON_INDEX THEN
                RAISE_APPLICATION_ERROR(bank_errors.c_duplicate_reference,
                    'Referenz ' || p_reference || ' existiert bereits');
        END;

        -- Soll (+) und Haben (-): Summe = 0
        INSERT INTO booking_line (booking_id, account_id, amount)
        VALUES (v_booking_id, p_debit.account_id, p_amount);

        INSERT INTO booking_line (booking_id, account_id, amount)
        VALUES (v_booking_id, p_credit.account_id, -p_amount);

        UPDATE account SET balance = balance - p_amount WHERE account_id = p_debit.account_id;
        UPDATE account SET balance = balance + p_amount WHERE account_id = p_credit.account_id;

        -- Outbox in derselben Transaktion: Event und Buchung gibt es nur zusammen.
        -- Event-Schema v1: keine internen IDs, nur fachliche Schlüssel (Referenz, Kontonummern).
        -- eventId ist im Payload gespeichert und bleibt bei jeder Wiederholung gleich (Idempotenz).
        INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, payload)
        SELECT 'BOOKING',
               p_reference,
               'BOOKING_CREATED',
               JSON_OBJECT(
                   'eventId'            VALUE LOWER(REGEXP_REPLACE(RAWTOHEX(SYS_GUID()),
                                               '(.{8})(.{4})(.{4})(.{4})(.{12})', '\1-\2-\3-\4-\5')),
                   'eventType'          VALUE 'BOOKING_CREATED',
                   'schemaVersion'      VALUE 1,
                   'occurredAt'         VALUE TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),
                                               'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"'),
                   'bookingReference'   VALUE p_reference,
                   'bookingType'        VALUE p_booking_type,
                   'debitAccountNumber' VALUE p_debit.account_number,
                   'creditAccountNumber' VALUE p_credit.account_number,
                   'amount'             VALUE p_amount,
                   'currency'           VALUE p_currency
                   RETURNING CLOB)
        FROM   dual;

        RETURN v_booking_id;
    END post_booking;

    FUNCTION deposit (
        p_account_id IN account.account_id%TYPE,
        p_amount     IN booking_line.amount%TYPE,
        p_currency   IN account.currency_code%TYPE,
        p_reference  IN booking.reference%TYPE
    ) RETURN booking.booking_id%TYPE
    IS
        v_cash_id  account.account_id%TYPE;
        v_cash     account%ROWTYPE;
        v_customer account%ROWTYPE;
    BEGIN
        check_amount(p_amount);

        -- Gegenkonto: interne Kasse in der Währung der Einzahlung
        BEGIN
            SELECT account_id
            INTO   v_cash_id
            FROM   account
            WHERE  account_type = 'INTERNAL'
            AND    account_number LIKE c_cash_pattern
            AND    currency_code = p_currency;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(bank_errors.c_currency_mismatch,
                    'Kein internes Kassenkonto für Währung ' || NVL(p_currency, 'NULL'));
        END;

        IF v_cash_id = p_account_id THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_same_account,
                'Einzahlung auf das Kassenkonto selbst ist nicht möglich');
        END IF;

        lock_accounts(p_account_id, v_cash_id, v_customer, v_cash);

        check_can_credit(v_customer);
        check_currency(v_customer, p_currency);
        check_can_debit(v_cash);

        RETURN post_booking('DEPOSIT', v_cash, v_customer,
                            p_amount, p_currency, p_reference);
    END deposit;

    FUNCTION transfer (
        p_from_account_id IN account.account_id%TYPE,
        p_to_account_id   IN account.account_id%TYPE,
        p_amount          IN booking_line.amount%TYPE,
        p_currency        IN account.currency_code%TYPE,
        p_reference       IN booking.reference%TYPE
    ) RETURN booking.booking_id%TYPE
    IS
        v_from account%ROWTYPE;
        v_to   account%ROWTYPE;
    BEGIN
        check_amount(p_amount);

        IF p_from_account_id = p_to_account_id THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_same_account,
                'Auftraggeber und Empfänger sind dasselbe Konto');
        END IF;

        lock_accounts(p_from_account_id, p_to_account_id, v_from, v_to);

        check_can_debit(v_from);
        check_can_credit(v_to);
        check_currency(v_from, p_currency);
        check_currency(v_to, p_currency);

        -- Deckung: Saldo plus Kreditlimite muss den Betrag abdecken
        IF v_from.balance + v_from.overdraft_limit < p_amount THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_insufficient_funds,
                'Konto ' || v_from.account_number || ': Saldo ' || v_from.balance
                || ' + Limite ' || v_from.overdraft_limit || ' deckt ' || p_amount || ' nicht');
        END IF;

        RETURN post_booking('TRANSFER', v_from, v_to,
                            p_amount, p_currency, p_reference);
    END transfer;

END booking_api;
/
