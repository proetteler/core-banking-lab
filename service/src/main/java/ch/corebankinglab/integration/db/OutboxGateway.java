package ch.corebankinglab.integration.db;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aufrufe von outbox_api. Wie beim CoreBankingGateway: nur EXECUTE auf das Package,
 * kein Tabellenzugriff, kein Commit. Die Transaktion steuert der Publisher.
 */
@Repository
public class OutboxGateway {

    private static final String SCHEMA = "CORE_BANK";

    private final JdbcTemplate jdbc;

    public OutboxGateway(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Holt und sperrt bis zu batchSize Events in ID-Reihenfolge. Die Sperre gilt bis zum Commit. */
    public List<OutboxEvent> fetchUnpublished(int batchSize) {
        return jdbc.execute("{? = call " + SCHEMA + ".outbox_api.fetch_unpublished(?)}",
                (CallableStatementCallback<List<OutboxEvent>>) cs -> {
                    cs.registerOutParameter(1, Types.REF_CURSOR);
                    cs.setInt(2, batchSize);
                    cs.execute();

                    List<OutboxEvent> events = new ArrayList<>();
                    try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                        while (rs.next()) {
                            events.add(new OutboxEvent(
                                    rs.getLong("outbox_event_id"),
                                    rs.getString("aggregate_id"),
                                    rs.getString("payload")));
                        }
                    }
                    return events;
                });
    }

    public void markPublished(long outboxEventId) {
        jdbc.execute("{call " + SCHEMA + ".outbox_api.mark_published(?)}",
                (CallableStatementCallback<Void>) cs -> {
                    cs.setLong(1, outboxEventId);
                    cs.execute();
                    return null;
                });
    }

    public OutboxBacklog backlog() {
        return jdbc.execute("BEGIN ? := " + SCHEMA + ".outbox_api.unpublished_count; ? := "
                        + SCHEMA + ".outbox_api.oldest_unpublished_age_seconds; END;",
                (CallableStatementCallback<OutboxBacklog>) cs -> {
                    cs.registerOutParameter(1, Types.NUMERIC);
                    cs.registerOutParameter(2, Types.NUMERIC);
                    cs.execute();
                    BigDecimal age = cs.getBigDecimal(2);
                    return new OutboxBacklog(cs.getLong(1), age == null ? null : age.longValue());
                });
    }
}
