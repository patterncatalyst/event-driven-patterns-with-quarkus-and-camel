package com.patterncatalyst.shipping.enrich.carriers;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The carrier reference data this service owns: transport mode and the tracking URL template. */
@Entity
@Table(name = "carrier")
public class CarrierRecord extends PanacheEntityBase {

    @Id
    public String carrier;

    public String mode;

    @Column(name = "tracking_url_template")
    public String trackingUrlTemplate;
}
