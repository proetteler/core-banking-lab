package ch.corebankinglab.integration.api;

import java.math.BigDecimal;

public record BalanceResponse(String accountNumber, BigDecimal balance) {
}
