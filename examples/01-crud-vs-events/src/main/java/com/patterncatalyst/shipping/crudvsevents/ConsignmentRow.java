package com.patterncatalyst.shipping.crudvsevents;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.ConsignmentStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The row model: the current state of one Consignment, updated in place. */
@Entity
@Table(name = "consignment")
public class ConsignmentRow extends PanacheEntityBase {

    @Id
    public String id;
    public String origin;
    public String destination;
    @Column(name = "weight_kg")
    public BigDecimal weightKg;
    public String carrier;
    @Enumerated(EnumType.STRING)
    public ConsignmentStatus status;
    @Version
    public long version;
}
