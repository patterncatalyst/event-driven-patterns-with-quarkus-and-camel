package com.patterncatalyst.shipping.saga.payment;

import java.math.BigDecimal;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;

import io.debezium.outbox.quarkus.ExportedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Takes the payment and remembers cancellations. The payment row, the cancellation row and the outbox row each commit
 * with the decision that wrote them. The order id is the key of both tables, so a repeated message changes nothing.
 */
@ApplicationScoped
@Named("paymentService")
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @Inject
    EntityManager em;

    @Inject
    Event<ExportedEvent<?, ?>> events;

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "payment.decline-above")
    BigDecimal declineAbove;

    @ConfigProperty(name = "payment.cancel-guard")
    String cancelGuard;

    /** Takes the payment for a reserved order, declines it above the limit, and refuses it for a cancelled order. */
    @Transactional
    public void take(InventoryReserved reserved) {
        if ("on".equals(cancelGuard) && cancelled(reserved.orderId())) {
            meters.counter("payment.refused", "reason", "order-cancelled").increment();
            LOG.infof("refused the payment for %s: the order was cancelled", reserved.orderId());
            return;
        }
        boolean declined = reserved.amount().compareTo(declineAbove) > 0;
        PaymentStatus status = declined ? PaymentStatus.FAILED : PaymentStatus.COMPLETED;
        int inserted = em.createNativeQuery("""
                INSERT INTO payments.payment (order_id, payment_id, amount, status) VALUES (:order, :payment, :amount, :status)
                ON CONFLICT (order_id) DO NOTHING""")
                .setParameter("order", reserved.orderId()).setParameter("payment", "P-" + reserved.orderId())
                .setParameter("amount", reserved.amount()).setParameter("status", status.name()).executeUpdate();
        if (inserted == 0) {
            LOG.infof("payment for %s already decided", reserved.orderId());
            return;
        }
        if (declined) {
            events.fire(PaymentExports.failed(reserved.orderId(), "DECLINED", json));
        } else {
            events.fire(PaymentExports.processed(
                    new Payment("P-" + reserved.orderId(), reserved.orderId(), reserved.amount(), status), json));
        }
        LOG.infof("payment for %s: %s", reserved.orderId(), status);
    }

    /** Records that the order is cancelled. Later payments for it are refused. */
    @Transactional
    public void recordCancelled(OrderCancelled cancelled) {
        em.createNativeQuery("INSERT INTO payments.cancelled_order (order_id) VALUES (:id) ON CONFLICT (order_id) DO NOTHING")
                .setParameter("id", cancelled.orderId()).executeUpdate();
    }

    private boolean cancelled(String orderId) {
        return !em.createNativeQuery("SELECT 1 FROM payments.cancelled_order WHERE order_id = :id")
                .setParameter("id", orderId).getResultList().isEmpty();
    }
}
