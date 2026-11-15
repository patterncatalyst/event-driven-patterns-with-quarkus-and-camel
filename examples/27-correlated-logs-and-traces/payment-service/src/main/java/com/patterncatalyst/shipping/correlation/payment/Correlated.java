package com.patterncatalyst.shipping.correlation.payment;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.MDC;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The business key of every handler. A step runs in a span that carries the order id as the attribute order.id, and
 * with the order id in the log context under the key orderId, which the OTLP log exporter sends as a log attribute.
 * The span has to be started here: the receive span of a Kafka record has ended before the handler runs, so an
 * attribute set on the current span in the handler is lost.
 */
@ApplicationScoped
public class Correlated {

    static final String KEY = "orderId";

    /** Demo code: off leaves the order id out of the log context, so logs can be found by trace id only. */
    @ConfigProperty(name = "correlation.order-id-in-logs", defaultValue = "on")
    String orderIdInLogs;

    /** A scope that removes the order id from the log context again. */
    public interface LogContext extends AutoCloseable {
        @Override
        void close();
    }

    @WithSpan("handle event")
    public void step(@SpanAttribute("order.id") String orderId, Runnable work) {
        try (LogContext ignored = logContext(orderId)) {
            work.run();
        }
    }

    public LogContext logContext(String orderId) {
        if ("on".equals(orderIdInLogs)) {
            MDC.put(KEY, orderId);
        }
        return () -> MDC.remove(KEY);
    }
}
