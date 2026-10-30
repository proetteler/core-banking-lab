CREATE OR REPLACE PACKAGE bank_errors
AS
    -- Zentrale Fehlerdefinitionen, Bereich -20000 .. -20999.
    -- Die Nummern sind die stabile Schnittstelle nach aussen (z. B. für den Java-Service).

    -- Fehlernummern
    c_account_not_found   CONSTANT PLS_INTEGER := -20001;
    c_account_blocked     CONSTANT PLS_INTEGER := -20002;
    c_insufficient_funds  CONSTANT PLS_INTEGER := -20003;
    c_currency_mismatch   CONSTANT PLS_INTEGER := -20004;
    c_invalid_amount      CONSTANT PLS_INTEGER := -20005;
    c_same_account        CONSTANT PLS_INTEGER := -20006;
    c_duplicate_reference CONSTANT PLS_INTEGER := -20007;
    c_outbox_event_not_found CONSTANT PLS_INTEGER := -20008;

    -- Benannte Exceptions, damit man im WHEN-Zweig nicht SQLCODE vergleichen muss
    account_not_found   EXCEPTION;
    account_blocked     EXCEPTION;
    insufficient_funds  EXCEPTION;
    currency_mismatch   EXCEPTION;
    invalid_amount      EXCEPTION;
    same_account        EXCEPTION;
    duplicate_reference EXCEPTION;
    outbox_event_not_found EXCEPTION;

    PRAGMA EXCEPTION_INIT (account_not_found,   -20001);
    PRAGMA EXCEPTION_INIT (account_blocked,     -20002);
    PRAGMA EXCEPTION_INIT (insufficient_funds,  -20003);
    PRAGMA EXCEPTION_INIT (currency_mismatch,   -20004);
    PRAGMA EXCEPTION_INIT (invalid_amount,      -20005);
    PRAGMA EXCEPTION_INIT (same_account,        -20006);
    PRAGMA EXCEPTION_INIT (duplicate_reference, -20007);
    PRAGMA EXCEPTION_INIT (outbox_event_not_found, -20008);
END bank_errors;
/
