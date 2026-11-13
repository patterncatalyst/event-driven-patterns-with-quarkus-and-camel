package com.patterncatalyst.shipping.replay;

import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One row of the read model: how many orders a carrier shipped on a UTC day. */
@Entity
@IdClass(CarrierDay.class)
@Table(name = "carrier_daily_shipments")
public class CarrierDailyShipments extends PanacheEntityBase {

    @Id
    public String carrier;

    @Id
    public LocalDate day;

    public long shipments;
}
