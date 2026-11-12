package com.patterncatalyst.shipping.aggregate;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Demo code, used only with RETRY_MODE=reappend: the negative witness. On a version conflict it appends the
 * cancellation that was decided against the old version at the new head, without deciding it again. A cancellation
 * decided at PAID then lands after a shipment.
 */
@ApplicationScoped
public class DemoReappend {

    @Inject
    OrderTransaction transaction;

    public OrderTransaction.Result handle(Command command) {
        try {
            return transaction.handle(command);
        } catch (ConcurrencyConflict conflict) {
            if (command instanceof Command.Cancel cancel) {
                return transaction.reappend(cancel.orderId(), List.of(new OrderEvent.OrderCancelled(cancel.reason())));
            }
            throw conflict;
        }
    }
}
