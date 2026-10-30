package ch.corebankinglab.integration.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class BankErrorTest {

    @ParameterizedTest
    @CsvSource({
            "20001, ACCOUNT_NOT_FOUND,     404",
            "20002, ACCOUNT_BLOCKED,       422",
            "20003, INSUFFICIENT_FUNDS,    422",
            "20004, CURRENCY_MISMATCH,     422",
            "20005, INVALID_AMOUNT,        422",
            "20006, SAME_ACCOUNT,          422",
            "20007, DUPLICATE_REFERENCE,   409"
    })
    void mapsOracleCodeToErrorAndStatus(int code, BankError expected, int expectedStatus) {
        assertThat(BankError.fromOracleCode(code)).contains(expected);
        assertThat(expected.status()).isEqualTo(HttpStatus.valueOf(expectedStatus));
    }

    @Test
    void acceptsNegativeCodeFromPlSql() {
        assertThat(BankError.fromOracleCode(-20003)).contains(BankError.INSUFFICIENT_FUNDS);
    }

    @Test
    void unknownCodesAreNotMapped() {
        // -20000 ist der generische Fehler der Installation, ORA-00001 ein echter DB-Fehler
        assertThat(BankError.fromOracleCode(20000)).isEqualTo(Optional.empty());
        assertThat(BankError.fromOracleCode(1)).isEqualTo(Optional.empty());
        assertThat(BankError.fromOracleCode(20999)).isEqualTo(Optional.empty());
    }
}
