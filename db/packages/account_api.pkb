CREATE OR REPLACE PACKAGE BODY account_api
AS
    FUNCTION get_balance (
        p_account_number IN account.account_number%TYPE
    ) RETURN account.balance%TYPE
    IS
        v_balance account.balance%TYPE;
    BEGIN
        SELECT balance
        INTO   v_balance
        FROM   account
        WHERE  account_number = p_account_number;

        RETURN v_balance;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(bank_errors.c_account_not_found,'Konto ' || p_account_number || ' nicht gefunden');
    END get_balance;

    PROCEDURE print_account (
        p_account_number IN account.account_number%TYPE
    )
    IS
        v_name     account.account_name%TYPE;
        v_currency account.currency_code%TYPE;
        v_balance  account.balance%TYPE;
    BEGIN
        SELECT account_name, currency_code, balance
        INTO   v_name, v_currency, v_balance
        FROM   account
        WHERE  account_number = p_account_number;

        DBMS_OUTPUT.PUT_LINE('Name: ' || v_name || ', Währung: ' || v_currency || ', Saldo: ' || v_balance);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('Kein Konto mit der Nummer ' || p_account_number || ' gefunden.');
    END print_account;

END account_api;
/