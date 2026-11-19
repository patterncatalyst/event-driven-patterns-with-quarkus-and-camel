package com.patterncatalyst.shipping.analysis;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The disposition currently on record for an exception: one row per exception id, rebuilt by {@link
 * BackfillService} from the latest analysis row every time the backfill endpoint runs, whether or not that run
 * called the model. {@link ReplayResource} reads this table in its default mode and calls the model for nothing.
 */
@Entity
@Table(name = "disposition_snapshot", schema = "analysis")
public class DispositionSnapshot extends PanacheEntityBase {

    @Id
    @Column(name = "exception_id")
    public String exceptionId;

    @Column(name = "order_id")
    public String orderId;

    @Enumerated(EnumType.STRING)
    public Disposition disposition;

    @Enumerated(EnumType.STRING)
    public Reason reason;

    public int attempt;

    @Column(name = "prompt_version")
    public String promptVersion;

    @Column(name = "model_digest")
    public String modelDigest;

    @Column(name = "updated_at")
    public Instant updatedAt;
}
