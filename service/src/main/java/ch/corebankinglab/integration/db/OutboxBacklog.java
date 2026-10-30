package ch.corebankinglab.integration.db;

/** Rückstand der Outbox. oldestAgeSeconds ist null, wenn nichts unveröffentlicht ist. */
public record OutboxBacklog(long unpublishedCount, Long oldestAgeSeconds) {
}
