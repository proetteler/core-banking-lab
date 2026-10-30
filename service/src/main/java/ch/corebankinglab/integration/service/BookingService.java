package ch.corebankinglab.integration.service;

import java.math.BigDecimal;

import ch.corebankinglab.integration.db.CoreBankingGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaktionsgrenze des Services. Die Packages booking_api/account_api committen bewusst nicht:
 * Commit bei Erfolg und Rollback bei Fehler (RuntimeException) macht hier @Transactional.
 * So gehören Namensauflösung, Buchung und Outbox-Event zu genau einer Transaktion.
 */
@Service
public class BookingService {

    private final CoreBankingGateway gateway;

    public BookingService(CoreBankingGateway gateway) {
        this.gateway = gateway;
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountNumber) {
        return gateway.getBalance(accountNumber);
    }

    @Transactional
    public long deposit(String accountNumber, BigDecimal amount, String currency, String reference) {
        // Die API kennt nur Kontonummern, die interne ID bleibt hinter dem Service
        long accountId = gateway.getAccountId(accountNumber);
        return gateway.deposit(accountId, amount, currency, reference);
    }

    @Transactional
    public long transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount,
                         String currency, String reference) {
        long fromId = gateway.getAccountId(fromAccountNumber);
        long toId = gateway.getAccountId(toAccountNumber);
        return gateway.transfer(fromId, toId, amount, currency, reference);
    }
}
