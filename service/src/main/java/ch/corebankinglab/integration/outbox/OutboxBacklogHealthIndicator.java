package ch.corebankinglab.integration.outbox;

import ch.corebankinglab.integration.db.OutboxBacklog;
import ch.corebankinglab.integration.db.OutboxGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Macht den Outbox-Rückstand sichtbar (Anzahl, Alter des ältesten Events), meldet aber nie DOWN.
 * Kafka ist keine harte Abhängigkeit: Buchungen funktionieren ohne Kafka, die Events warten in
 * der Outbox. Ein Ausfall von Kafka soll den Service darum nicht aus dem Load Balancer nehmen.
 * Der Rückstand wird überwacht (Alarm auf Alter/Anzahl), nicht als "Service down" gemeldet.
 * Details nur unter /actuator/health/outbox.
 */
@Component("outboxBacklog")
public class OutboxBacklogHealthIndicator implements HealthIndicator {

    private final OutboxGateway gateway;
    private final long warnAfterSeconds;

    public OutboxBacklogHealthIndicator(OutboxGateway gateway,
                                        @Value("${outbox.backlog.warn-after-seconds:300}") long warnAfterSeconds) {
        this.gateway = gateway;
        this.warnAfterSeconds = warnAfterSeconds;
    }

    @Override
    public Health health() {
        try {
            OutboxBacklog backlog = gateway.backlog();
            boolean lagging = backlog.oldestAgeSeconds() != null && backlog.oldestAgeSeconds() > warnAfterSeconds;
            return Health.up()
                    .withDetail("unpublishedCount", backlog.unpublishedCount())
                    .withDetail("oldestAgeSeconds", backlog.oldestAgeSeconds() == null ? 0 : backlog.oldestAgeSeconds())
                    .withDetail("lagging", lagging)
                    .withDetail("warnAfterSeconds", warnAfterSeconds)
                    .build();
        } catch (Exception e) {
            // UNKNOWN verschlechtert den Gesamtstatus nicht, die DB-Probleme meldet der DB-Indikator
            return Health.unknown().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
