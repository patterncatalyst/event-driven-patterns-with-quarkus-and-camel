package com.patterncatalyst.shipping.inbox;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An event id that has been applied. The primary key is the guard. */
@Entity
@Table(name = "inbox")
public class InboxEntry extends PanacheEntityBase {

    @Id
    @Column(name = "event_id")
    public UUID eventId;
}
