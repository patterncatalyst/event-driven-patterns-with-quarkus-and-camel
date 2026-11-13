package com.patterncatalyst.shipping.replay;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Where the projector stopped: the last (transaction, position) it applied. */
@Entity
@Table(name = "projection_checkpoint")
public class ProjectionCheckpoint extends PanacheEntityBase {

    public static final String CARRIER_DAILY = "carrier-daily";

    @Id
    public String name;

    @Column(name = "last_tx_id")
    public long lastTxId;

    @Column(name = "last_position")
    public long lastPosition;

    @Column(name = "updated_at")
    public Instant updatedAt;
}
