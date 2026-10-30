CREATE OR REPLACE PACKAGE account_api
AS
    FUNCTION get_balance (
        p_account_number IN account.account_number%TYPE
    ) RETURN account.balance%TYPE;

    -- Löst die fachliche Kontonummer in die interne ID auf; unbekannt -> bank_errors.account_not_found
    FUNCTION get_account_id (
        p_account_number IN account.account_number%TYPE
    ) RETURN account.account_id%TYPE;

    PROCEDURE print_account (
        p_account_number IN account.account_number%TYPE
    );
END account_api;
/
