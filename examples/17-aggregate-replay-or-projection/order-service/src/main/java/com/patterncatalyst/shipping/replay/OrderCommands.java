package com.patterncatalyst.shipping.replay;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Load the stream, rebuild the aggregate, decide, append at the loaded version, in one transaction. */
@ApplicationScoped
public class OrderCommands {

    @Inject
    EventStore store;

    @Inject
    DemoHold hold;

    /** Returns the version of the stream after the command. */
    @Transactional
    public int handle(Command command) {
        OrderAggregate order = OrderAggregate.rehydrate(store.load(command.orderId()));
        int loaded = order.version();
        List<OrderEvent> events = order.decide(command);
        store.append("Order", command.orderId(), loaded, events);
        hold.afterAppend(command.orderId(), command.name());
        return loaded + events.size();
    }
}
