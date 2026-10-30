-- Smoke-Test für booking_api. Voraussetzung: install.sql und testdata/01_demo_customers_accounts.sql.
-- Verändert keine Daten: am Ende ROLLBACK. Bei Fehler Exit-Code <> 0 (CI-tauglich).
SET SERVEROUTPUT ON SIZE UNLIMITED
SET FEEDBACK OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
WHENEVER OSERROR  EXIT FAILURE ROLLBACK

DECLARE
    v_anna_chf    account.account_id%TYPE;
    v_anna_eur    account.account_id%TYPE;
    v_ben_chf     account.account_id%TYPE;
    v_ben_blocked account.account_id%TYPE;
    v_clara_chf   account.account_id%TYPE;

    v_booking_id  booking.booking_id%TYPE;
    v_raised      BOOLEAN;
    v_bal_anna    NUMBER;
    v_bal_ben     NUMBER;
    v_count       NUMBER;
    v_outbox_before NUMBER;
    v_outbox_after  NUMBER;
    v_deposit_id  booking.booking_id%TYPE;

    FUNCTION acc_id (p_number IN VARCHAR2) RETURN account.account_id%TYPE
    IS
        v_id account.account_id%TYPE;
    BEGIN
        SELECT account_id INTO v_id FROM account WHERE account_number = p_number;
        RETURN v_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20900, 'Konto ' || p_number || ' fehlt, Testdaten geladen?');
    END acc_id;

    FUNCTION bal (p_id IN account.account_id%TYPE) RETURN NUMBER
    IS
        v_bal account.balance%TYPE;
    BEGIN
        SELECT balance INTO v_bal FROM account WHERE account_id = p_id;
        RETURN v_bal;
    END bal;

    PROCEDURE check_that (p_cond IN BOOLEAN, p_msg IN VARCHAR2)
    IS
    BEGIN
        IF NOT NVL(p_cond, FALSE) THEN
            RAISE_APPLICATION_ERROR(-20900, 'FEHLGESCHLAGEN: ' || p_msg);
        END IF;
        DBMS_OUTPUT.PUT_LINE('ok  ' || p_msg);
    END check_that;
BEGIN
    v_anna_chf    := acc_id('CUS-1001-01');
    v_anna_eur    := acc_id('CUS-1001-02');
    v_ben_chf     := acc_id('CUS-1002-01');
    v_ben_blocked := acc_id('CUS-1002-02');
    v_clara_chf   := acc_id('CUS-1003-01');

    SELECT COUNT(*) INTO v_outbox_before FROM outbox_event;
    v_bal_anna := bal(v_anna_chf);
    v_bal_ben  := bal(v_ben_chf);

    -- Positivtests
    v_deposit_id := booking_api.deposit(v_anna_chf, 1000, 'CHF', 'SMOKE-DEP-1');
    check_that(v_deposit_id IS NOT NULL, 'deposit liefert Buchungs-ID');
    check_that(bal(v_anna_chf) = v_bal_anna + 1000, 'deposit: Saldo Anna +1000');

    v_booking_id := booking_api.transfer(v_anna_chf, v_ben_chf, 300, 'CHF', 'SMOKE-TRF-1');
    check_that(bal(v_anna_chf) = v_bal_anna + 700, 'transfer: Saldo Anna -300');
    check_that(bal(v_ben_chf)  = v_bal_ben + 300,  'transfer: Saldo Ben +300');

    -- Kreditlimite 500: genau bis zur Limite erlaubt, 1 Rappen mehr nicht
    v_booking_id := booking_api.transfer(v_clara_chf, v_ben_chf, 500, 'CHF', 'SMOKE-TRF-2');
    check_that(bal(v_clara_chf) = -500, 'transfer bis zur Kreditlimite erlaubt');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.transfer(v_clara_chf, v_ben_chf, 0.01, 'CHF', 'SMOKE-TRF-3');
    EXCEPTION
        WHEN bank_errors.insufficient_funds THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'transfer über Kreditlimite -> insufficient_funds');

    -- Ohne Deckung: Fehler und beide Salden unverändert (alles-oder-nichts)
    v_bal_anna := bal(v_anna_chf);
    v_bal_ben  := bal(v_ben_chf);
    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.transfer(v_anna_chf, v_ben_chf, 1000000, 'CHF', 'SMOKE-TRF-4');
    EXCEPTION
        WHEN bank_errors.insufficient_funds THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'transfer ohne Deckung -> insufficient_funds');
    check_that(bal(v_anna_chf) = v_bal_anna AND bal(v_ben_chf) = v_bal_ben,
               'nach insufficient_funds sind die Salden unverändert');

    -- Gesperrtes Konto: Belastung verboten, Gutschrift erlaubt
    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.transfer(v_ben_blocked, v_ben_chf, 1, 'CHF', 'SMOKE-BLK-1');
    EXCEPTION
        WHEN bank_errors.account_blocked THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'Belastung gesperrtes Konto -> account_blocked');

    v_booking_id := booking_api.deposit(v_ben_blocked, 50, 'CHF', 'SMOKE-BLK-2');
    check_that(bal(v_ben_blocked) = 50, 'Einzahlung auf BLOCKED_DEBIT-Konto erlaubt');

    -- Währung
    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.deposit(v_anna_chf, 10, 'EUR', 'SMOKE-CUR-1');
    EXCEPTION
        WHEN bank_errors.currency_mismatch THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'deposit EUR auf CHF-Konto -> currency_mismatch');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.transfer(v_anna_chf, v_anna_eur, 10, 'CHF', 'SMOKE-CUR-2');
    EXCEPTION
        WHEN bank_errors.currency_mismatch THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'transfer CHF auf EUR-Konto -> currency_mismatch');

    v_booking_id := booking_api.deposit(v_anna_eur, 250, 'EUR', 'SMOKE-CUR-3');
    check_that(bal(v_anna_eur) = 250, 'deposit EUR auf EUR-Konto (Kasse EUR)');

    -- Weitere Regeln
    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.transfer(v_anna_chf, v_anna_chf, 1, 'CHF', 'SMOKE-SAME-1');
    EXCEPTION
        WHEN bank_errors.same_account THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'transfer auf dasselbe Konto -> same_account');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.deposit(v_anna_chf, 0, 'CHF', 'SMOKE-AMT-1');
    EXCEPTION
        WHEN bank_errors.invalid_amount THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'Betrag 0 -> invalid_amount');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.deposit(v_anna_chf, -5, 'CHF', 'SMOKE-AMT-2');
    EXCEPTION
        WHEN bank_errors.invalid_amount THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'negativer Betrag -> invalid_amount');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.deposit(v_anna_chf, 1.234, 'CHF', 'SMOKE-AMT-3');
    EXCEPTION
        WHEN bank_errors.invalid_amount THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'Betrag mit 3 Nachkommastellen -> invalid_amount');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.deposit(-1, 10, 'CHF', 'SMOKE-NF-1');
    EXCEPTION
        WHEN bank_errors.account_not_found THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'unbekanntes Konto -> account_not_found');

    v_raised := FALSE;
    BEGIN
        v_booking_id := booking_api.deposit(v_anna_chf, 10, 'CHF', 'SMOKE-DEP-1');
    EXCEPTION
        WHEN bank_errors.duplicate_reference THEN v_raised := TRUE;
    END;
    check_that(v_raised, 'doppelte Referenz -> duplicate_reference');

    -- Doppelte Buchführung
    SELECT SUM(amount) INTO v_count FROM booking_line;
    check_that(NVL(v_count, 0) = 0, 'Summe aller Buchungszeilen = 0');

    SELECT COUNT(*) INTO v_count
    FROM  (SELECT booking_id FROM booking_line GROUP BY booking_id HAVING SUM(amount) <> 0);
    check_that(v_count = 0, 'jede Buchung einzeln: Summe = 0');

    SELECT COUNT(*) INTO v_count
    FROM   account a
    WHERE  a.balance <> -NVL((SELECT SUM(l.amount) FROM booking_line l WHERE l.account_id = a.account_id), 0);
    check_that(v_count = 0, 'Saldo jedes Kontos = -Summe seiner Buchungszeilen');

    -- Outbox: genau ein Event pro erfolgreicher Buchung, keines für abgelehnte
    SELECT COUNT(*) INTO v_outbox_after FROM outbox_event;
    check_that(v_outbox_after - v_outbox_before = 5, 'Outbox: 5 neue Events für 5 erfolgreiche Buchungen');

    SELECT COUNT(*) INTO v_count
    FROM   outbox_event
    WHERE  aggregate_type = 'BOOKING'
    AND    aggregate_id   = TO_CHAR(v_deposit_id)
    AND    event_type     = 'BOOKING_CREATED'
    AND    published_at IS NULL
    AND    JSON_VALUE(payload, '$.amount' RETURNING NUMBER) = 1000
    AND    JSON_VALUE(payload, '$.reference') = 'SMOKE-DEP-1';
    check_that(v_count = 1, 'Outbox: Event der Einzahlung mit gültigem JSON-Payload');

    DBMS_OUTPUT.PUT_LINE('SMOKE TEST OK');
END;
/

ROLLBACK;
EXIT
