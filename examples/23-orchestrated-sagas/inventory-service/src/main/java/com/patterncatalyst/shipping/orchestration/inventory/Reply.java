package com.patterncatalyst.shipping.orchestration.inventory;

import com.patterncatalyst.shipping.Topics;

/** The answer to ReserveStock. The outcome is in the payload, so a workflow can branch on it. */
public record Reply(String orderId, String outcome) {

    public static final String RESERVED = "RESERVED";
    public static final String INSUFFICIENT = "INSUFFICIENT";

    public String topic() {
        return RESERVED.equals(outcome) ? Topics.INVENTORY_RESERVED : Topics.INVENTORY_INSUFFICIENT;
    }

    public String eventType() {
        return RESERVED.equals(outcome) ? "InventoryReserved" : "InventoryInsufficient";
    }
}
