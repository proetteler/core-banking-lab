package ch.corebankinglab.integration.payment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sendet das Ergebnis einer Order nach core-banking.payment-results und wartet auf die Bestätigung.
 * Key = reference, so bleiben Ergebnisse derselben Order in Reihenfolge.
 * Scheitert das Senden, wirft die Methode: die Order wird wiederholt. Hat die Buchung da schon
 * stattgefunden, kommt beim Retry DUPLICATE. DUPLICATE heisst also "Referenz ist gebucht".
 */
@Component
public class PaymentResultPublisher {

    private static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String topic;
    private final long sendTimeoutMs;

    @Autowired
    public PaymentResultPublisher(KafkaTemplate<String, String> kafka,
                                  ObjectMapper objectMapper,
                                  @Value("${app.kafka.topics.payment-results}") String topic,
                                  @Value("${outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMs) {
        this(kafka, objectMapper, Clock.systemUTC(), topic, sendTimeoutMs);
    }

    PaymentResultPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper, Clock clock,
                           String topic, long sendTimeoutMs) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.topic = topic;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public void publish(PaymentResult result) {
        String payload = toJson(result);
        try {
            kafka.send(topic, result.reference(), payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending payment result", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Payment result was not acknowledged by Kafka", e);
        }
    }

    String toJson(PaymentResult result) {
        var event = new PaymentResultEvent(
                UUID.randomUUID().toString(),
                "PAYMENT_RESULT",
                1,
                ISO_MILLIS.format(Instant.now(clock)),
                result.reference(),
                result.status().name(),
                result.errorCode());
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize payment result", e);
        }
    }
}
