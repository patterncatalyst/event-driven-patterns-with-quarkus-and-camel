package com.patterncatalyst.shipping;

import java.math.BigDecimal;

/** A customer's order for one item. The amount is held at two decimals. */
public record Order(String id, String customerId, String itemSku, int quantity, BigDecimal amount) {

    public Order {
        Checks.text(id, "id");
        Checks.text(customerId, "customerId");
        Checks.text(itemSku, "itemSku");
        Checks.positive(quantity, "quantity");
        amount = Checks.money(amount, "amount");
    }
}
