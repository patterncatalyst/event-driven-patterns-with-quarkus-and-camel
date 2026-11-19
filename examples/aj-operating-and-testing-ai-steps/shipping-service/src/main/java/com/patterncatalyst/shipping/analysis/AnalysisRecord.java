package com.patterncatalyst.shipping.analysis;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.patterncatalyst.shipping.Carrier;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One committed analysis. The primary key (exceptionId, promptVersion, modelDigest) is the exactly-once guard:
 * {@link MemoizedAnalysisService} looks a row up by this key before calling the model, so a redelivered or
 * duplicated note reuses the row instead of paying for a second call. The table is append-only: a later prompt
 * version or model writes a new row for the same exception id, never overwrites an old one.
 */
@Entity
@Table(name = "analysis", schema = "analysis")
@IdClass(AnalysisRecord.Key.class)
public class AnalysisRecord extends PanacheEntityBase {

    @Id
    @Column(name = "exception_id")
    public String exceptionId;

    @Id
    @Column(name = "prompt_version")
    public String promptVersion;

    @Id
    @Column(name = "model_digest")
    public String modelDigest;

    /** Database-assigned insertion order, read only: the tiebreaker {@link #latestPerException()} orders by. */
    @Column(name = "seq", insertable = false, updatable = false)
    public Long seq;

    @Column(name = "order_id")
    public String orderId;

    @Column(name = "shipment_id")
    public String shipmentId;

    @Enumerated(EnumType.STRING)
    public Carrier carrier;

    public int attempt;

    @Enumerated(EnumType.STRING)
    public Reason reason;

    public String evidence;

    public String model;

    @Column(name = "occurred_at")
    public Instant occurredAt;

    public static AnalysisRecord findByKey(String exceptionId, String promptVersion, String modelDigest) {
        return find("exceptionId = ?1 and promptVersion = ?2 and modelDigest = ?3", exceptionId, promptVersion, modelDigest)
                .firstResult();
    }

    /** The row with the highest insertion order for each exception id on record: the latest analysis of each. */
    public static List<AnalysisRecord> latestPerException() {
        return getEntityManager().createQuery(
                "select a from AnalysisRecord a where a.seq = (select max(b.seq) from AnalysisRecord b where b.exceptionId = a.exceptionId)",
                AnalysisRecord.class).getResultList();
    }

    public static AnalysisRecord from(DeliveryExceptionAnalysed analysed) {
        var row = new AnalysisRecord();
        row.exceptionId = analysed.exceptionId();
        row.promptVersion = analysed.promptVersion();
        row.modelDigest = analysed.modelDigest();
        row.orderId = analysed.orderId();
        row.shipmentId = analysed.shipmentId();
        row.carrier = analysed.carrier();
        row.attempt = analysed.attempt();
        row.reason = analysed.reason();
        row.evidence = analysed.evidence();
        row.model = analysed.model();
        row.occurredAt = analysed.occurredAt();
        return row;
    }

    /** Reused when a committed row answers a lookup instead of the model: the derivedFrom fields mark the reuse. */
    public DeliveryExceptionAnalysed toEvent() {
        return new DeliveryExceptionAnalysed(exceptionId, orderId, shipmentId, carrier, attempt, reason, evidence, model,
                modelDigest, promptVersion, occurredAt, promptVersion, modelDigest);
    }

    public static class Key implements Serializable {
        public String exceptionId;
        public String promptVersion;
        public String modelDigest;

        public Key() {
        }

        public Key(String exceptionId, String promptVersion, String modelDigest) {
            this.exceptionId = exceptionId;
            this.promptVersion = promptVersion;
            this.modelDigest = modelDigest;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(exceptionId, k.exceptionId)
                    && Objects.equals(promptVersion, k.promptVersion) && Objects.equals(modelDigest, k.modelDigest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exceptionId, promptVersion, modelDigest);
        }
    }
}
