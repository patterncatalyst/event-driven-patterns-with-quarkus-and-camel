package com.patterncatalyst.shipping.queues;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patterncatalyst.shipping.Order;

/** An order-placed event read as a notification job: the order id and the order, whose customer is the recipient. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryJob(String orderId, Order order) {

    public String customerId() {
        return order.customerId();
    }
}
