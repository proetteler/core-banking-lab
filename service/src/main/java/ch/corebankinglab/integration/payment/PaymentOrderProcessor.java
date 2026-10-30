package ch.corebankinglab.integration.payment;

import java.util.Set;
import java.util.stream.Collectors;

import ch.corebankinglab.integration.api.TransferRequest;
import ch.corebankinglab.integration.error.BankError;
import ch.corebankinglab.integration.error.BankException;
import ch.corebankinglab.integration.service.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verarbeitet eine Payment-Order und klassifiziert das Ergebnis.
 * Die Buchung läuft über BookingService.transfer, also dieselbe Logik und Transaktion wie bei REST.
 *
 * Klassifizierung:
 *  - Buchung ok                          -> BOOKED
 *  - duplicate_reference                 -> DUPLICATE (idempotent, nicht nochmal buchen)
 *  - anderer Fachfehler (BankException)  -> REJECTED mit Fehlercode, kein Retry
 *  - Order lesbar, aber Felder ungültig  -> REJECTED INVALID_ORDER
 *  - kein JSON / keine Referenz          -> PoisonMessageException (direkt ins DLT)
 *  - technischer Fehler (DB, Timeout)    -> Exception bleibt unverändert, der Error-Handler wiederholt
 */
@Component
public class PaymentOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderProcessor.class);
    static final String INVALID_ORDER = "INVALID_ORDER";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final BookingService bookingService;

    public PaymentOrderProcessor(ObjectMapper objectMapper, Validator validator, BookingService bookingService) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.bookingService = bookingService;
    }

    public PaymentResult process(String json) {
        TransferRequest order = parse(json);

        Set<ConstraintViolation<TransferRequest>> violations = validator.validate(order);
        if (!violations.isEmpty()) {
            log.info("Rejected invalid payment order {}: {}", order.reference(), describe(violations));
            return PaymentResult.rejected(order.reference(), INVALID_ORDER);
        }

        try {
            bookingService.transfer(order.fromAccountNumber(), order.toAccountNumber(),
                    order.amount(), order.currency(), order.reference());
            return PaymentResult.booked(order.reference());
        } catch (BankException e) {
            if (e.error() == BankError.DUPLICATE_REFERENCE) {
                return PaymentResult.duplicate(order.reference());
            }
            log.info("Rejected payment order {}: {}", order.reference(), e.error());
            return PaymentResult.rejected(order.reference(), e.error().name());
        }
    }

    private TransferRequest parse(String json) {
        if (json == null) {
            throw new PoisonMessageException("Empty payment order");
        }
        TransferRequest order;
        try {
            order = objectMapper.readValue(json, TransferRequest.class);
        } catch (JsonProcessingException e) {
            throw new PoisonMessageException("Payment order is not valid JSON", e);
        }
        // Ohne Referenz gibt es keinen Schlüssel für das Ergebnis, ein Retry hilft nicht
        if (order == null || order.reference() == null || order.reference().isBlank()) {
            throw new PoisonMessageException("Payment order has no reference");
        }
        return order;
    }

    private String describe(Set<ConstraintViolation<TransferRequest>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
