package com.patterncatalyst.shipping.replay;

import java.sql.Connection;

/**
 * Two writers, two connections. A inserts first and holds its transaction open, so its row has the lower position.
 * B inserts next and commits. The projector runs, then A commits and it runs again.
 */
abstract class GapBase extends ProjectionBase {

    /** Returns {total counted after B committed, total counted after A committed} for the year's shipments. */
    long[] gap(int year, String orderA, String orderB) throws Exception {
        try (Connection a = ds.getConnection(); Connection b = ds.getConnection()) {
            Histories.placed(a, orderA);
            Histories.paid(a, orderA);
            Histories.placed(b, orderB);
            Histories.paid(b, orderB);
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            Histories.shipped(a, orderA, "AIRCO", year + "-02-02T10:00:00Z");
            Histories.shipped(b, orderB, "AIRCO", year + "-02-02T10:00:01Z");
            b.commit();
            drain(1000);
            long afterB = Histories.total(Histories.ofYear(store.rows(), year));
            a.commit();
            drain(1000);
            long afterA = Histories.total(Histories.ofYear(store.rows(), year));
            return new long[] {afterB, afterA};
        }
    }
}
