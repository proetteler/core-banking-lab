package ch.corebankinglab.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ch.corebankinglab.integration.db.OutboxEvent;
import ch.corebankinglab.integration.db.OutboxGateway;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@SuppressWarnings("unchecked")
class OutboxPublisherTest {

    private final OutboxGateway gateway = mock(OutboxGateway.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final OutboxPublisher publisher =
            new OutboxPublisher(gateway, kafka, txManager, "events", 50, 1000);

    private final List<OutboxEvent> events = List.of(
            new OutboxEvent(1, "DEP-1", "{\"n\":1}"),
            new OutboxEvent(2, "TRF-1", "{\"n\":2}"));

    OutboxPublisherTest() {
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void sendsWithReferenceAsKeyThenMarksThenCommits() {
        when(gateway.fetchUnpublished(50)).thenReturn(events);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(publisher.publishBatch()).isEqualTo(2);

        InOrder order = inOrder(kafka, gateway, txManager);
        order.verify(kafka).send("events", "DEP-1", "{\"n\":1}");
        order.verify(kafka).send("events", "TRF-1", "{\"n\":2}");
        order.verify(gateway).markPublished(1);
        order.verify(gateway).markPublished(2);
        order.verify(txManager).commit(any());
    }

    @Test
    void doesNotMarkAndRollsBackWhenKafkaDoesNotAcknowledge() {
        when(gateway.fetchUnpublished(50)).thenReturn(events);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("no ack")));

        assertThatThrownBy(publisher::publishBatch).isInstanceOf(OutboxPublishException.class);

        verify(gateway, never()).markPublished(anyLong());
        verify(txManager).rollback(any());
        verify(txManager, never()).commit(any());
    }

    @Test
    void pollSwallowsKafkaOutageSoTheServiceKeepsRunning() {
        when(gateway.fetchUnpublished(50)).thenReturn(events);
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("broker unreachable"));

        publisher.poll();

        verify(gateway, never()).markPublished(anyLong());
        verify(txManager).rollback(any());
    }

    @Test
    void emptyOutboxSendsNothing() {
        when(gateway.fetchUnpublished(50)).thenReturn(List.of());

        assertThat(publisher.publishBatch()).isZero();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
