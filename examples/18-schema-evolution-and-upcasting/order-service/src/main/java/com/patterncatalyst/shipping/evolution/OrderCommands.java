package com.patterncatalyst.shipping.evolution;

import java.math.BigDecimal;

import org.jboss.logging.Logger;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** One command, one transaction: load (upcast), decide, append in the current shape, and publish OrderPlaced. */
@ApplicationScoped
public class OrderCommands {

    private static final Logger LOG = Logger.getLogger(OrderCommands.class);

    public record PlaceRequest(String id, String customerId, String itemSku, int quantity, BigDecimal amount,
            String shippingPriority) {
    }

    @Inject
    EventStore store;

    @Inject
    OrderPlacedPublisher publisher;

    @Inject
    ReleaseSchema release;

    @Transactional
    public OrderAggregate load(String orderId) {
        return OrderAggregate.rehydrate(store.load(orderId));
    }

    @Transactional
    public void place(PlaceRequest request) {
        if (load(request.id()).version() != 0) {
            throw new CommandRefused("order exists");
        }
        Order order = new Order(request.id(), request.customerId(), request.itemSku(), request.quantity(),
                request.amount());
        String priority = request.shippingPriority() == null ? Upcasters.DEFAULT_PRIORITY : request.shippingPriority();
        store.append(order.id(), 1, "OrderPlaced", release.storedSchemaVersion(), release.storedPayload(order, priority));
        publisher.publish(order, priority); // a registry refusal throws here and the whole transaction rolls back
        LOG.infof("placed %s at schema_version %d", order.id(), release.storedSchemaVersion());
    }

    @Transactional
    public void cancel(String orderId) {
        OrderAggregate order = load(orderId);
        if (order.status() != OrderStatus.PLACED) {
            throw new CommandRefused(order.status() == null ? "no such order" : order.status() + " to CANCELLED");
        }
        store.append(orderId, order.version() + 1, "OrderCancelled", Upcasters.CURRENT,
                new OrderEvent.OrderCancelled("customer"));
    }
}
