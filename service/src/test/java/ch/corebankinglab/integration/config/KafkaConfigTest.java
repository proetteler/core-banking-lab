package ch.corebankinglab.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.corebankinglab.integration.payment.PoisonMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

@SuppressWarnings("unchecked")
class KafkaConfigTest {

    @Test
    void deadLetterTopicIsNamedWithDotDltAndKeepsThePartition() {
        var record = new ConsumerRecord<>("core-banking.payment-orders", 2, 7L, "k", "v");

        TopicPartition target = KafkaConfig.deadLetterDestination(record, new RuntimeException());

        assertThat(target).isEqualTo(new TopicPartition("core-banking.payment-orders.DLT", 2));
    }

    @Test
    void poisonMessagesAreNotRetriedButTechnicalErrorsAre() {
        DefaultErrorHandler handler = new KafkaConfig().kafkaErrorHandler(mock(KafkaTemplate.class));

        // removeClassification liefert die Einstufung: false = nicht wiederholen, null = Standard (Retry)
        assertThat(handler.removeClassification(PoisonMessageException.class)).isFalse();
        assertThat(handler.removeClassification(QueryTimeoutException.class)).isNull();
        // Eingebauter Standard von Spring: Deserialisierungsfehler werden ebenfalls nie wiederholt
        assertThat(handler.removeClassification(DeserializationException.class)).isFalse();
    }
}
