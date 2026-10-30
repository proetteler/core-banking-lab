package ch.corebankinglab.integration.outbox;

/** Batch wurde nicht von Kafka bestätigt. RuntimeException, damit die Transaktion zurückrollt. */
public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
