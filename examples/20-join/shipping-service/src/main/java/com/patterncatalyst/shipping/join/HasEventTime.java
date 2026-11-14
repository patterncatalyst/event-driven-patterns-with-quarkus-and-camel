package com.patterncatalyst.shipping.join;

import java.time.Instant;

/** A payload that says when its fact happened, which can differ from when the record reached Kafka. */
public interface HasEventTime {

    Instant occurredAt();
}
