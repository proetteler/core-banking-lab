package ch.corebankinglab.integration.db;

/** Zeile aus outbox_api.fetch_unpublished. Die ID ist intern und dient nur zum Markieren. */
public record OutboxEvent(long id, String aggregateId, String payload) {
}
