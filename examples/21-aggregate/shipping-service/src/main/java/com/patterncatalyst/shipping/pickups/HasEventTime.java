package com.patterncatalyst.shipping.pickups;

import java.time.Instant;

/** A payload that says when the business fact happened, as opposed to when the record was written. */
public interface HasEventTime {

    Instant occurredAt();
}
