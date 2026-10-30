package ch.corebankinglab.integration.payment;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Nachricht auf core-banking.payment-results (Schema v1, siehe api/asyncapi.yaml). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResultEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        String occurredAt,
        String reference,
        String status,
        String errorCode) {
}
