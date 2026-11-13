package com.patterncatalyst.shipping.replay;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The read model: the report is a table scan, and the projector adds to it. */
@ApplicationScoped
public class CarrierDailyStore implements PanacheRepositoryBase<CarrierDailyShipments, CarrierDay> {

    @Inject
    EntityManager em;

    public List<ShipmentsPerDay> rows() {
        return listAll(Sort.by("carrier").and("day")).stream()
                .map(r -> new ShipmentsPerDay(r.carrier, r.day, r.shipments)).toList();
    }

    /** Adds to a row, creating it on first sight. One statement; Panache has no upsert. */
    public void add(String carrier, LocalDate day, long shipments) {
        em.createNativeQuery("""
                INSERT INTO orders.carrier_daily_shipments (carrier, day, shipments) VALUES (:c, :d, :n)
                ON CONFLICT (carrier, day) DO UPDATE SET shipments = carrier_daily_shipments.shipments + EXCLUDED.shipments
                """).setParameter("c", carrier).setParameter("d", day).setParameter("n", shipments).executeUpdate();
    }
}
