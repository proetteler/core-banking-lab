CREATE OR REPLACE PACKAGE account_api
AS
    FUNCTION get_balance (
        p_account_number IN account.account_number%TYPE
    ) RETURN account.balance%TYPE;

    PROCEDURE print_account (
        p_account_number IN account.account_number%TYPE
    );
END account_api;
/
