package com.patterncatalyst.shipping.replay;

import java.time.LocalDate;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Chooses which committed events a batch may read. Positions are handed out when a row is inserted, not when its
 * transaction commits, so a later position can become visible before an earlier one. Reading by (tx_id, position)
 * only below the oldest transaction still running means every row below that point is already visible and will
 * never be joined by a late one.
 *
 * <p>PROJECTION_WATERMARK=off is demo code: it reads by position alone, which skips the late commit.
 */
@ApplicationScoped
public class ProjectionWatermark {

    /** A row the projector reads: its place in the order of reading, and what it needs to count. */
    public record Pending(long position, long txId, String type, String carrier, LocalDate day) {
    }

    private static final String SELECT = """
            SELECT position, tx_id::text::bigint AS tx, type, payload->>'carrier', (occurred_at AT TIME ZONE 'UTC')::date
            FROM orders.events
            """;

    private static final String BELOW_THE_WATERMARK = SELECT + """
            WHERE (tx_id, position) > (CAST(CAST(:t AS text) AS xid8), :p)
              AND tx_id < pg_snapshot_xmin(pg_current_snapshot())
            ORDER BY events.tx_id, events.position LIMIT :lim
            """;

    private static final String BY_POSITION = SELECT + """
            WHERE position > :p ORDER BY position LIMIT :lim
            """;

    @ConfigProperty(name = "projection.watermark")
    String mode;

    @Inject
    EntityManager em;

    public boolean enabled() {
        return !"off".equals(mode);
    }

    @SuppressWarnings("unchecked")
    public List<Pending> read(long afterTx, long afterPosition, int limit) {
        Query query = em.createNativeQuery(enabled() ? BELOW_THE_WATERMARK : BY_POSITION)
                .setParameter("p", afterPosition).setParameter("lim", limit);
        if (enabled()) {
            query.setParameter("t", afterTx);
        }
        return ((List<Object[]>) query.getResultList()).stream()
                .map(r -> new Pending(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), (String) r[2],
                        (String) r[3], r[4] == null ? null : LocalDate.parse(r[4].toString())))
                .toList();
    }
}
