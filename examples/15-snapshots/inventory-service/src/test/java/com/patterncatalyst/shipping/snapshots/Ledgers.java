package com.patterncatalyst.shipping.snapshots;

import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** Test helpers: a unique SKU per test (events are append-only, so nothing is cleaned up) and bulk-loaded history. */
final class Ledgers {

    private Ledgers() {
    }

    static String newSku() {
        return "SKU-T" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** n events: version 1 receives 100, every tenth receives 10, the rest reserve 1. Returns the quantity on hand. */
    static int seed(EntityManager em, String sku, int n) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO inventory.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
                SELECT gen_random_uuid(), 'InventoryItem', ?1, v,
                  CASE WHEN v = 1 OR v % 10 = 0 THEN 'StockReceived' ELSE 'StockReserved' END,
                  CASE WHEN v = 1 THEN jsonb_build_object('kind','StockReceived','quantity',100)
                       WHEN v % 10 = 0 THEN jsonb_build_object('kind','StockReceived','quantity',10)
                       ELSE jsonb_build_object('kind','StockReserved','orderId','O-' || v,'quantity',1) END,
                  now()
                FROM generate_series(1, ?2) AS v
                """).setParameter(1, sku).setParameter(2, n).executeUpdate());
        return 100 + 10 * (n / 10) - (n - 1 - n / 10);
    }
}
