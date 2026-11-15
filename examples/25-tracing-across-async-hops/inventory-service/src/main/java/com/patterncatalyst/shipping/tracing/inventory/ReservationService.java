package com.patterncatalyst.shipping.tracing.inventory;

import org.jboss.logging.Logger;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Holds the stock for an order. The span is created by the annotation on a separate bean: the consumer's own span
 * has ended before this runs, so an attribute set on the current span there would be lost.
 */
@ApplicationScoped
public class ReservationService {

    private static final Logger LOG = Logger.getLogger(ReservationService.class);

    @WithSpan("reserve stock")
    @Transactional
    public boolean reserve(@SpanAttribute("order.id") String orderId, String sku, int quantity) {
        long taken = StockItem.update("quantityOnHand = quantityOnHand - ?1 where sku = ?2 and quantityOnHand >= ?1",
                quantity, sku);
        LOG.infof("%s %d of %s for %s", taken == 1 ? "reserved" : "could not reserve", quantity, sku, orderId);
        return taken == 1;
    }
}
