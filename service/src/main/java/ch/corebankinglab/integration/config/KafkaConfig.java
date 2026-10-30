package ch.corebankinglab.integration.config;

import ch.corebankinglab.integration.payment.PoisonMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Fehlerverhalten des Payment-Order-Consumers:
     *  - technische Fehler: 3 Wiederholungen mit Backoff (1 s, 2 s, 4 s), danach Dead-Letter-Topic
     *    <topic>.DLT (gleiche Partition, deshalb gleich viele Partitionen wie das Quell-Topic)
     *  - PoisonMessageException: kein Retry, sofort ins DLT
     * Fachfehler kommen hier nie an, sie werden als REJECTED-Ergebnis gemeldet.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template, KafkaConfig::deadLetterDestination), backOff);
        handler.addNotRetryableExceptions(PoisonMessageException.class);
        return handler;
    }

    /**
     * Der Spring-Standard heisst seit 3.x "<topic>-dlt". Unser Topic heisst "<topic>.DLT" (von kafka-init
     * angelegt), darum der Name explizit. Gleiche Partition wie das Original.
     */
    static TopicPartition deadLetterDestination(ConsumerRecord<?, ?> record, Exception ex) {
        return new TopicPartition(record.topic() + ".DLT", record.partition());
    }
}
