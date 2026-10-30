package ch.corebankinglab.integration.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ch.corebankinglab.integration.db.OutboxEvent;
import ch.corebankinglab.integration.db.OutboxGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional-Outbox-Relay: liest unveröffentlichte Events aus der DB und sendet sie nach Kafka.
 *
 * Ablauf pro Batch, alles in einer DB-Transaktion:
 *   holen und sperren -> senden -> auf alle Bestätigungen (acks=all) warten -> markieren -> Commit.
 * Scheitert etwas vor dem Commit, gibt es Rollback ohne Markierung: die Events bleiben
 * unveröffentlicht und werden beim nächsten Lauf erneut versucht.
 *
 * Garantie: AT-LEAST-ONCE. Stürzt der Service nach dem Senden, aber vor dem Commit ab, wird dasselbe
 * Event erneut gesendet. Konsumenten müssen darum über die eventId (im Payload) idempotent sein.
 *
 * Key = aggregateId = bookingReference. Gleicher Key landet in derselben Partition, damit bleiben
 * alle Events einer Buchung zusammen und in Reihenfolge. Die Referenz ist der fachliche Schlüssel
 * und verrät keine interne ID. Eine globale Reihenfolge über Buchungen hinweg gibt es bewusst nicht.
 *
 * Kafka ist keine harte Abhängigkeit: Ist es nicht erreichbar, endet der Lauf mit einer Warnung,
 * der Service (REST, Buchungen) läuft weiter und der Rückstand wächst, siehe OutboxBacklogHealthIndicator.
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxGateway gateway;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final String topic;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPublisher(OutboxGateway gateway,
                           KafkaTemplate<String, String> kafka,
                           PlatformTransactionManager transactionManager,
                           @Value("${app.kafka.topics.booking-events}") String topic,
                           @Value("${outbox.publisher.batch-size:50}") int batchSize,
                           @Value("${outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.gateway = gateway;
        this.kafka = kafka;
        this.tx = new TransactionTemplate(transactionManager);
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:2000}")
    public void poll() {
        try {
            int total = 0;
            int sent;
            do {
                sent = publishBatch();
                total += sent;
            } while (sent == batchSize);
            if (total > 0) {
                log.info("Published {} outbox events to {}", total, topic);
            }
        } catch (Exception e) {
            // Kein Absturz: die Events bleiben unveröffentlicht und werden beim nächsten Lauf versucht
            log.warn("Outbox publishing failed, events stay unpublished and will be retried: {}", e.toString());
        }
    }

    /** Ein Batch in einer Transaktion. Package-sichtbar für Tests. */
    int publishBatch() {
        Integer count = tx.execute(status -> {
            List<OutboxEvent> events = gateway.fetchUnpublished(batchSize);
            if (events.isEmpty()) {
                return 0;
            }

            List<CompletableFuture<?>> sends = new ArrayList<>(events.size());
            for (OutboxEvent event : events) {
                sends.add(kafka.send(topic, event.aggregateId(), event.payload()));
            }
            awaitAll(sends);

            // Erst jetzt, nach der Bestätigung durch Kafka, wird markiert
            events.forEach(event -> gateway.markPublished(event.id()));
            return events.size();
        });
        return count == null ? 0 : count;
    }

    private void awaitAll(List<CompletableFuture<?>> sends) {
        try {
            CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]))
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Interrupted while waiting for Kafka acks", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxPublishException("Kafka did not acknowledge the batch", e);
        }
    }
}
