package com.patterncatalyst.shipping.snapshots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * DEMO CODE, not part of the pattern. These endpoints exist so verify.sh can measure a load inside the service
 * and can make a stale snapshot write happen on purpose. A real service would not expose them.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    @Inject
    StockLedger ledger;

    @Inject
    ReservationCommands commands;

    @Inject
    SnapshotStore store;

    /**
     * Full and snapshot loads of one stream, alternating in the same JVM, timed inside the service. The first
     * {@code warm} pairs are discarded; the others give the median, minimum and maximum in microseconds.
     */
    @GET
    @Path("/bench/{sku}")
    public Map<String, Object> bench(@PathParam("sku") String sku, @QueryParam("warm") @DefaultValue("5") int warm,
            @QueryParam("runs") @DefaultValue("21") int runs,
            @QueryParam("snapshot") @DefaultValue("true") boolean withSnapshot) {
        List<Long> full = new ArrayList<>();
        List<Long> snapshot = new ArrayList<>();
        long fullEvents = 0;
        long snapshotEvents = 0;
        for (int i = 0; i < warm + runs; i++) {
            var f = ledger.load(sku, false);
            var s = withSnapshot ? ledger.load(sku, true) : null;
            if (i >= warm) {
                full.add(f.stats().loadNanos());
                fullEvents = f.stats().eventsReplayed();
                if (s != null) {
                    snapshot.add(s.stats().loadNanos());
                    snapshotEvents = s.stats().eventsReplayed();
                }
            }
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("sku", sku);
        out.put("runs", runs);
        out.put("fullEvents", fullEvents);
        summarise(out, "full", full);
        if (!snapshot.isEmpty()) {
            out.put("snapshotEvents", snapshotEvents);
            summarise(out, "snapshot", snapshot);
        }
        return out;
    }

    private static void summarise(Map<String, Object> out, String name, List<Long> nanos) {
        Collections.sort(nanos);
        out.put(name + "MedianUs", nanos.get(nanos.size() / 2) / 1000);
        out.put(name + "MinUs", nanos.get(0) / 1000);
        out.put(name + "MaxUs", nanos.get(nanos.size() - 1) / 1000);
    }

    /**
     * A snapshot taken at version v is written after {@code moves} commands have moved the stream, and the inline
     * policy has stored a newer one. The guard in the upsert decides whether the older one replaces it.
     */
    @POST
    @Path("/stale-take/{sku}")
    public Map<String, Object> staleTake(@PathParam("sku") String sku, @QueryParam("moves") @DefaultValue("100") int moves) {
        var stale = ledger.load(sku, true).aggregate();
        for (int i = 0; i < moves; i++) {
            commands.reserve(sku, "O-DEMO-" + i, 1);
        }
        int before = storedVersion(sku);
        int written = writeStale(stale);
        return Map.of("staleVersion", stale.version(), "snapshotBefore", before, "rowsWritten", written,
                "snapshotAfter", storedVersion(sku));
    }

    @Transactional
    int writeStale(InventoryItemAggregate aggregate) {
        return store.upsert(aggregate, "stale");
    }

    @Transactional
    int storedVersion(String sku) {
        SnapshotRow row = SnapshotRow.findById(sku);
        return row == null ? 0 : row.version;
    }
}
