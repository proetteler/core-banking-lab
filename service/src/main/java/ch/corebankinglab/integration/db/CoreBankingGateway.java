package ch.corebankinglab.integration.db;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;

import ch.corebankinglab.integration.error.BankError;
import ch.corebankinglab.integration.error.BankException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Einziger Ort, der die PL/SQL-Packages aufruft. Der DB-User CORE_BANK_API hat nur EXECUTE
 * auf die Packages, darum immer mit Schema-Präfix und ohne direkten Tabellenzugriff.
 * Die Methoden committen nicht, die Transaktion steuert der aufrufende Service.
 */
@Repository
public class CoreBankingGateway {

    private static final String SCHEMA = "CORE_BANK";

    private final JdbcTemplate jdbc;

    public CoreBankingGateway(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long getAccountId(String accountNumber) {
        return call("{? = call " + SCHEMA + ".account_api.get_account_id(?)}", cs -> {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setString(2, accountNumber);
            cs.execute();
            return cs.getLong(1);
        });
    }

    public BigDecimal getBalance(String accountNumber) {
        return call("{? = call " + SCHEMA + ".account_api.get_balance(?)}", cs -> {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setString(2, accountNumber);
            cs.execute();
            return cs.getBigDecimal(1);
        });
    }

    public long deposit(long accountId, BigDecimal amount, String currency, String reference) {
        return call("{? = call " + SCHEMA + ".booking_api.deposit(?, ?, ?, ?)}", cs -> {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setLong(2, accountId);
            cs.setBigDecimal(3, amount);
            cs.setString(4, currency);
            cs.setString(5, reference);
            cs.execute();
            return cs.getLong(1);
        });
    }

    public long transfer(long fromAccountId, long toAccountId, BigDecimal amount, String currency, String reference) {
        return call("{? = call " + SCHEMA + ".booking_api.transfer(?, ?, ?, ?, ?)}", cs -> {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setLong(2, fromAccountId);
            cs.setLong(3, toAccountId);
            cs.setBigDecimal(4, amount);
            cs.setString(5, currency);
            cs.setString(6, reference);
            cs.execute();
            return cs.getLong(1);
        });
    }

    private <T> T call(String sql, CallableStatementCallback<T> action) {
        try {
            return jdbc.execute(sql, action);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /** ORA-20001..20007 aus bank_errors werden zu BankException, alles andere bleibt unverändert. */
    static RuntimeException translate(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                var error = BankError.fromOracleCode(sql.getErrorCode());
                if (error.isPresent()) {
                    return new BankException(error.get(), firstLine(sql.getMessage()));
                }
            }
        }
        return e;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return null;
        }
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }
}
