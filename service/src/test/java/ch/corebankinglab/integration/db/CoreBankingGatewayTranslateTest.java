package ch.corebankinglab.integration.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import ch.corebankinglab.integration.error.BankError;
import ch.corebankinglab.integration.error.BankException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;

class CoreBankingGatewayTranslateTest {

    @Test
    void translatesBankErrorFromSqlException() {
        var sql = new SQLException("ORA-20003: Konto X: Saldo 0 deckt 5 nicht\nORA-06512: at line 1", "72000", 20003);
        DataAccessException ex = new UncategorizedSQLException("CallableStatementCallback", "{call ...}", sql);

        RuntimeException result = CoreBankingGateway.translate(ex);

        assertThat(result).isInstanceOf(BankException.class);
        assertThat(((BankException) result).error()).isEqualTo(BankError.INSUFFICIENT_FUNDS);
        // Nur die erste Zeile der DB-Meldung wird behalten, ohne Stack
        assertThat(result.getMessage()).doesNotContain("ORA-06512");
    }

    @Test
    void keepsOtherDatabaseErrorsUntouched() {
        var sql = new SQLException("ORA-00942: table or view does not exist", "42000", 942);
        DataAccessException ex = new UncategorizedSQLException("CallableStatementCallback", "{call ...}", sql);

        assertThat(CoreBankingGateway.translate(ex)).isSameAs(ex);
    }
}
