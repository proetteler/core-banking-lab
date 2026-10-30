package ch.corebankinglab.integration.error;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpStatus;

/**
 * Fehlernummern aus dem PL/SQL-Package bank_errors und ihr HTTP-Status.
 * Die Nummern müssen mit db/packages/bank_errors.pks übereinstimmen.
 * Der Detailtext ist bewusst fest und kommt nicht aus der DB-Meldung (die enthält z. B. Salden).
 */
public enum BankError {

    ACCOUNT_NOT_FOUND(20001, HttpStatus.NOT_FOUND, "account-not-found", "Account not found"),
    ACCOUNT_BLOCKED(20002, HttpStatus.UNPROCESSABLE_ENTITY, "account-blocked", "Account is blocked for this operation"),
    INSUFFICIENT_FUNDS(20003, HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds", "Insufficient funds"),
    CURRENCY_MISMATCH(20004, HttpStatus.UNPROCESSABLE_ENTITY, "currency-mismatch", "Currency does not match the account"),
    INVALID_AMOUNT(20005, HttpStatus.UNPROCESSABLE_ENTITY, "invalid-amount", "Invalid amount"),
    SAME_ACCOUNT(20006, HttpStatus.UNPROCESSABLE_ENTITY, "same-account", "Source and target account are the same"),
    DUPLICATE_REFERENCE(20007, HttpStatus.CONFLICT, "duplicate-reference", "A booking with this reference already exists");

    private final int oracleCode;
    private final HttpStatus status;
    private final String slug;
    private final String detail;

    BankError(int oracleCode, HttpStatus status, String slug, String detail) {
        this.oracleCode = oracleCode;
        this.status = status;
        this.slug = slug;
        this.detail = detail;
    }

    /** Oracle liefert RAISE_APPLICATION_ERROR(-20001, ...) als positiven Code 20001 im JDBC. */
    public static Optional<BankError> fromOracleCode(int code) {
        int positive = Math.abs(code);
        return Arrays.stream(values()).filter(e -> e.oracleCode == positive).findFirst();
    }

    public HttpStatus status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public String detail() {
        return detail;
    }
}
