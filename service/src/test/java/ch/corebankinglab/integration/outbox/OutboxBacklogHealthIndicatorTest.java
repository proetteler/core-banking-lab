package ch.corebankinglab.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.corebankinglab.integration.db.OutboxBacklog;
import ch.corebankinglab.integration.db.OutboxGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class OutboxBacklogHealthIndicatorTest {

    private final OutboxGateway gateway = mock(OutboxGateway.class);
    private final OutboxBacklogHealthIndicator indicator = new OutboxBacklogHealthIndicator(gateway, 300);

    @Test
    void emptyBacklogIsUpAndNotLagging() {
        when(gateway.backlog()).thenReturn(new OutboxBacklog(0, null));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("unpublishedCount", 0L)
                .containsEntry("oldestAgeSeconds", 0L).containsEntry("lagging", false);
    }

    @Test
    void oldBacklogStaysUpButIsMarkedAsLagging() {
        when(gateway.backlog()).thenReturn(new OutboxBacklog(42, 900L));

        Health health = indicator.health();

        // Kafka ist keine harte Abhängigkeit: auch ein grosser Rückstand macht den Service nicht DOWN
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("unpublishedCount", 42L)
                .containsEntry("oldestAgeSeconds", 900L).containsEntry("lagging", true);
    }

    @Test
    void failingQueryIsUnknownNeverDown() {
        when(gateway.backlog()).thenThrow(new IllegalStateException("db gone"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }
}
