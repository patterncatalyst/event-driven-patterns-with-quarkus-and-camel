package com.patterncatalyst.shipping.notificationservice;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.patterncatalyst.shipping.OrderStatus;

/** The answer to GET /order-copies/{id}: every Order field, the status and the attachment's claim if any. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderCopyView(String id, String customerId, String itemSku, int quantity, BigDecimal amount,
        OrderStatus status, Claim attachment) {

    static OrderCopyView of(OrderCopy c) {
        Claim claim = c.attachmentClaimId == null ? null : new Claim(c.attachmentClaimId.toString(), c.attachmentSize);
        return new OrderCopyView(c.id, c.customerId, c.itemSku, c.quantity, c.amount, c.status, claim);
    }
}
