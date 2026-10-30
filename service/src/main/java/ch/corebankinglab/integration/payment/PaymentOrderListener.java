package ch.corebankinglab.integration.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Konsumiert core-banking.payment-orders (at-least-once). Eine Order kann doppelt ankommen,
 * die Referenz macht die Verarbeitung idempotent (DUPLICATE statt zweiter Buchung).
 * Exceptions gehen an den DefaultErrorHandler (KafkaConfig): Retry mit Backoff, dann Dead-Letter-Topic.
 */
@Component
public class PaymentOrderListener {

    private final PaymentOrderProcessor processor;
    private final PaymentResultPublisher resultPublisher;

    public PaymentOrderListener(PaymentOrderProcessor processor, PaymentResultPublisher resultPublisher) {
        this.processor = processor;
        this.resultPublisher = resultPublisher;
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-orders}")
    public void onOrder(ConsumerRecord<String, String> record) {
        PaymentResult result = processor.process(record.value());
        resultPublisher.publish(result);
    }
}
