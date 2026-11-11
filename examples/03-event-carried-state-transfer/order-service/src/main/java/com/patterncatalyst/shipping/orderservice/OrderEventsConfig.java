package com.patterncatalyst.shipping.orderservice;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** ORDER_EVENTS_CARRY_STATE and ORDER_EVENTS_ATTACHMENT in the environment. */
@ConfigMapping(prefix = "order-events")
public interface OrderEventsConfig {

    /** False publishes the thin event: the order id and nothing else. */
    @WithDefault("true")
    boolean carryState();

    /** How a large attachment travels: as a claim, or inline in the event. */
    @WithDefault("claim-check")
    AttachmentMode attachment();

    enum AttachmentMode {
        CLAIM_CHECK, INLINE
    }
}
