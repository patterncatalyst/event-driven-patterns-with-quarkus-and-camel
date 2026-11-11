package com.patterncatalyst.shipping.orderservice;

import java.time.Instant;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The large, optional part of an Order, stored once. Events carry its id, never its content. */
@Entity
@Table(name = "claim_check")
public class ClaimCheck extends PanacheEntityBase {

    @Id
    public UUID id;
    @Column(name = "order_id")
    public String orderId;
    public String content;
    @Column(name = "created_at")
    public Instant createdAt;

    static ClaimCheck store(String orderId, String content) {
        ClaimCheck claim = new ClaimCheck();
        claim.id = UUID.randomUUID();
        claim.orderId = orderId;
        claim.content = content;
        claim.createdAt = Instant.now();
        claim.persist();
        return claim;
    }
}
